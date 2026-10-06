import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { forkJoin, Observable, of, Subject } from 'rxjs';
import { finalize, map, switchMap, takeUntil } from 'rxjs/operators';
import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import { toProblemDetail } from '../../../core/interceptors/error.interceptor';
import {
  AdminBook,
  AdminBookStatus,
  CategoryResponse,
  CreateBookRequest,
  FieldError,
  IsbnMetadataResponse,
  PageResponse,
  UpdateBookRequest,
} from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { httpUrlValidator } from '../../../shared/validators/http-url.validator';
import { isValidIsbnOptional, normalizeIsbn } from '../../../shared/validators/isbn';
import { isbnValidator } from '../../../shared/validators/isbn.validator';
import { buildCreateBookBody, buildUpdateBookBody, roundMoney2 } from './admin-book-form-body';
import { buildIsbnLookupApply, namesEqualTr } from './admin-book-isbn-lookup';

const LOOKUP_SIZE = 100;
const MAX_AUTHOR_NAMES = 20;

type PageState = 'loading' | 'ready' | 'notFound' | 'error';

@Component({
  selector: 'app-admin-book-form-page',
  templateUrl: './admin-book-form-page.component.html',
  styleUrls: ['./admin-book-form-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminBookFormPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private stockModalRef: NgbModalRef | null = null;
  /** DB’den gelen ISBN; checksum geçersiz olsa da formu kilitlememek için validator’a verilir. */
  private loadedIsbn: string | null = null;

  @ViewChild('stockModal') stockModal!: TemplateRef<unknown>;

  pageState: PageState = 'loading';
  isCreate = true;
  bookId: string | null = null;
  book: AdminBook | null = null;
  saving = false;
  actionBusy = false;
  isbnLookupBusy = false;
  isbnLookupHints: string[] = [];
  fieldErrors: FieldError[] = [];
  concurrentConflict = false;
  stockSaving = false;
  authorDraft = '';
  /** Kayıttan sonra multipart yüklenecek yerel dosya (OL URL yerine tercih). */
  pendingCoverFile: File | null = null;

  categories: CategoryResponse[] = [];

  readonly form: FormGroup = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(300)]],
    priceAmount: [null as number | null, [Validators.required, Validators.min(0)]],
    authorNames: [[] as string[]],
    categoryIds: [[] as string[]],
    coverUrl: ['', [Validators.maxLength(500), httpUrlValidator()]],
    description: ['', [Validators.maxLength(10000)]],
    isbn: ['', [isbnValidator(() => this.loadedIsbn)]],
    pageCount: [null as number | null, [Validators.min(1)]],
    initialStock: [0, [Validators.min(0), Validators.max(1000000)]],
    version: [null as number | null],
  });

  readonly stockForm: FormGroup = this.fb.group({
    delta: [
      null as number | null,
      [Validators.required, Validators.min(-100000), Validators.max(100000)],
    ],
  });

  constructor(
    private readonly api: AdminCatalogApi,
    private readonly fb: FormBuilder,
    private readonly modal: NgbModal,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly toast: ToastService,
    private readonly cdr: ChangeDetectorRef,
  ) {
    this.form.statusChanges.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.cdr.markForCheck();
    });
  }

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        takeUntil(this.destroy$),
        switchMap((params) => {
          const id = params.get('id');
          this.pageState = 'loading';
          this.fieldErrors = [];
          this.concurrentConflict = false;
          this.cdr.markForCheck();

          if (!id) {
            this.isCreate = true;
            this.bookId = null;
            this.book = null;
            this.resetCreateForm();
            return this.loadLookups().pipe(map(() => null as AdminBook | null));
          }

          this.isCreate = false;
          this.bookId = id;
          return this.loadLookups().pipe(switchMap(() => this.api.getBook(id)));
        }),
      )
      .subscribe({
        next: (book) => {
          if (!this.isCreate && book) {
            this.patchFromBook(book);
          }
          this.pageState = 'ready';
          this.cdr.markForCheck();
        },
        error: (err: unknown) => {
          const problem = toProblemDetail(err);
          this.pageState = problem.status === 404 ? 'notFound' : 'error';
          this.cdr.markForCheck();
        },
      });
  }

  ngOnDestroy(): void {
    this.stockModalRef?.dismiss();
    this.destroy$.next();
    this.destroy$.complete();
  }

  get canSave(): boolean {
    return this.form.valid && !this.saving && !this.actionBusy;
  }

  get pageTitle(): string {
    return this.isCreate ? 'Yeni kitap' : 'Kitabı düzenle';
  }

  get canPublish(): boolean {
    return !!this.book && (this.book.status === 'draft' || this.book.status === 'archived');
  }

  get canArchive(): boolean {
    return !!this.book && this.book.status === 'published';
  }

  /** Yüklenen ISBN checksum’sız; kullanıcı değiştirmeden kaydederse korunur. */
  get hasLegacyInvalidIsbn(): boolean {
    if (this.isCreate || !this.loadedIsbn) {
      return false;
    }
    const current = String(this.form.get('isbn')?.value ?? '');
    const cur = normalizeIsbn(current);
    const prev = normalizeIsbn(this.loadedIsbn);
    return !isValidIsbnOptional(this.loadedIsbn) && cur != null && prev != null && cur === prev;
  }

  statusLabel(status: AdminBookStatus): string {
    switch (status) {
      case 'draft':
        return 'Taslak';
      case 'published':
        return 'Yayında';
      case 'archived':
        return 'Arşiv';
      default:
        return status;
    }
  }

  isCategorySelected(id: string): boolean {
    return (this.form.get('categoryIds')?.value as string[]).includes(id);
  }

  get authorNames(): string[] {
    return (this.form.get('authorNames')?.value as string[]) ?? [];
  }

  addAuthorName(): void {
    const name = this.authorDraft.trim();
    if (!name || this.saving || this.actionBusy) {
      return;
    }
    const current = [...this.authorNames];
    if (current.some((existing) => namesEqualTr(existing, name))) {
      this.authorDraft = '';
      this.cdr.markForCheck();
      return;
    }
    if (current.length >= MAX_AUTHOR_NAMES) {
      this.toast.error('En fazla 20 yazar eklenebilir.');
      return;
    }
    current.push(name);
    this.form.get('authorNames')?.setValue(current);
    this.form.get('authorNames')?.markAsDirty();
    this.authorDraft = '';
    this.cdr.markForCheck();
  }

  onAuthorDraftKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.addAuthorName();
    }
  }

  removeAuthorName(index: number): void {
    const current = [...this.authorNames];
    if (index < 0 || index >= current.length) {
      return;
    }
    current.splice(index, 1);
    this.form.get('authorNames')?.setValue(current);
    this.form.get('authorNames')?.markAsDirty();
    this.cdr.markForCheck();
  }

  toggleCategory(id: string, checked: boolean): void {
    this.toggleId('categoryIds', id, checked);
  }

  onLookupIsbn(): void {
    const raw = String(this.form.get('isbn')?.value ?? '').trim();
    if (!raw || this.isbnLookupBusy || this.saving || this.actionBusy) {
      return;
    }
    this.isbnLookupBusy = true;
    this.isbnLookupHints = [];
    this.fieldErrors = this.fieldErrors.filter((e) => e.field !== 'isbn');
    this.cdr.markForCheck();
    this.api
      .lookupBookByIsbn(raw)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.isbnLookupBusy = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (meta) => this.applyIsbnMetadata(meta),
        error: (err: unknown) => {
          const problem = toProblemDetail(err);
          if (problem.code === 'VALIDATION_FAILED' && problem.errors?.length) {
            this.fieldErrors = [...problem.errors];
          } else if (problem.code === 'BOOK_METADATA_NOT_FOUND') {
            this.toast.error('Bu ISBN için Open Library’de bilgi bulunamadı.');
          } else {
            this.toast.error('Kitap bilgileri alınamadı. Biraz sonra tekrar deneyin.');
          }
        },
      });
  }

  onCancel(): void {
    void this.router.navigate(['/admin/books']);
  }

  reloadBook(): void {
    if (!this.bookId) {
      return;
    }
    this.pageState = 'loading';
    this.concurrentConflict = false;
    this.fieldErrors = [];
    this.cdr.markForCheck();
    this.api
      .getBook(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (book) => {
          this.patchFromBook(book);
          this.pageState = 'ready';
          this.cdr.markForCheck();
        },
        error: (err: unknown) => {
          const problem = toProblemDetail(err);
          this.pageState = problem.status === 404 ? 'notFound' : 'error';
          this.cdr.markForCheck();
        },
      });
  }

  onPublish(): void {
    if (!this.bookId || !this.book || this.actionBusy || !this.canPublish) {
      return;
    }
    const previous = this.book.status;
    this.actionBusy = true;
    this.cdr.markForCheck();
    this.api
      .publishBook(this.bookId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.actionBusy = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (book) => {
          if (book.status !== previous) {
            this.toast.success('Kitap yayınlandı');
          }
          this.patchFromBook(book);
        },
        error: () => undefined,
      });
  }

  onArchive(): void {
    if (!this.bookId || !this.book || this.actionBusy || !this.canArchive) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Kitabı arşivle',
        message: `"${this.book.title}" arşivlenecek ve vitrinden kalkacak. Emin misiniz?`,
        confirmLabel: 'Arşivle',
        cancelLabel: 'Vazgeç',
        confirmButtonClass: 'btn-warning',
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((ok) => {
        if (!ok || !this.bookId || !this.book) {
          return;
        }
        const previous = this.book.status;
        this.actionBusy = true;
        this.cdr.markForCheck();
        this.api
          .archiveBook(this.bookId)
          .pipe(
            takeUntil(this.destroy$),
            finalize(() => {
              this.actionBusy = false;
              this.cdr.markForCheck();
            }),
          )
          .subscribe({
            next: (book) => {
              if (book.status !== previous) {
                this.toast.success('Kitap arşivlendi');
              }
              this.patchFromBook(book);
            },
            error: () => undefined,
          });
      });
  }

  openStockAdjust(): void {
    if (!this.book || this.actionBusy || !this.stockModal) {
      return;
    }
    this.stockForm.reset({ delta: null });
    this.stockModalRef?.dismiss();
    this.stockModalRef = this.modal.open(this.stockModal, {
      centered: true,
      backdrop: 'static',
    });
    this.stockModalRef.result.finally(() => {
      this.stockModalRef = null;
      this.stockSaving = false;
      this.cdr.markForCheck();
    });
    this.cdr.markForCheck();
  }

  closeStockModal(): void {
    this.stockModalRef?.dismiss();
  }

  get canSubmitStock(): boolean {
    const delta = this.stockForm.get('delta')?.value;
    return (
      this.stockForm.valid &&
      !this.stockSaving &&
      delta !== null &&
      delta !== '' &&
      Number(delta) !== 0
    );
  }

  onStockSubmit(): void {
    this.stockForm.markAllAsTouched();
    this.cdr.markForCheck();
    if (!this.canSubmitStock || !this.bookId) {
      return;
    }
    const delta = Number(this.stockForm.get('delta')?.value);
    this.stockSaving = true;
    this.actionBusy = true;
    this.cdr.markForCheck();
    this.api
      .adjustBookStock(this.bookId, { delta })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.stockSaving = false;
          this.actionBusy = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (book) => {
          this.toast.success('Stok güncellendi');
          this.stockModalRef?.close();
          this.patchFromBook(book);
        },
        error: () => undefined,
      });
  }

  onCoverFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.pendingCoverFile = file;
    this.cdr.markForCheck();
  }

  onSubmit(): void {
    this.form.markAllAsTouched();
    this.cdr.markForCheck();
    if (!this.canSave) {
      return;
    }

    this.saving = true;
    this.fieldErrors = [];
    this.concurrentConflict = false;
    this.cdr.markForCheck();

    const wasCreate = this.isCreate;
    const pendingFile = this.pendingCoverFile;
    const request$ = wasCreate
      ? this.api.createBook(this.toCreateBody(!!pendingFile))
      : this.api.updateBook(this.bookId as string, this.toUpdateBody());

    request$
      .pipe(
        takeUntil(this.destroy$),
        switchMap((book) => {
          if (pendingFile && book.id) {
            return this.api.uploadBookCover(book.id, pendingFile);
          }
          return of(book);
        }),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (book) => {
          this.pendingCoverFile = null;
          this.toast.success(wasCreate ? 'Kitap oluşturuldu' : 'Kitap güncellendi');
          if (wasCreate) {
            void this.router.navigate(['/admin/books', book.id]);
          } else {
            this.patchFromBook(book);
          }
        },
        error: (err: unknown) => {
          const problem = toProblemDetail(err);
          this.fieldErrors = problem.errors ?? [];
          if (problem.status === 409 && problem.code === 'CONCURRENT_MODIFICATION') {
            this.concurrentConflict = true;
          }
          this.cdr.markForCheck();
        },
      });
  }

  private toggleId(controlName: 'categoryIds', id: string, checked: boolean): void {
    const current = [...(this.form.get(controlName)?.value as string[])];
    const idx = current.indexOf(id);
    if (checked && idx < 0) {
      if (current.length >= 20) {
        this.toast.error('En fazla 20 seçim yapılabilir.');
        return;
      }
      current.push(id);
    } else if (!checked && idx >= 0) {
      current.splice(idx, 1);
    }
    this.form.get(controlName)?.setValue(current);
    this.form.get(controlName)?.markAsDirty();
    this.cdr.markForCheck();
  }

  private applyIsbnMetadata(meta: IsbnMetadataResponse): void {
    const { patch, hints } = buildIsbnLookupApply(
      meta,
      this.categories,
      this.form.get('isbn')?.value,
      namesEqualTr,
    );
    this.form.patchValue(patch);
    this.form.markAsDirty();
    this.isbnLookupHints = hints;
    this.toast.success('ISBN bilgileri forma yazıldı.');
    this.cdr.markForCheck();
  }

  private resetCreateForm(): void {
    this.loadedIsbn = null;
    this.authorDraft = '';
    this.pendingCoverFile = null;
    this.form.reset({
      title: '',
      priceAmount: null,
      authorNames: [],
      categoryIds: [],
      coverUrl: '',
      description: '',
      isbn: '',
      pageCount: null,
      initialStock: 0,
      version: null,
    });
    this.isbnLookupHints = [];
  }

  private patchFromBook(book: AdminBook): void {
    this.book = book;
    this.loadedIsbn = book.isbn || null;
    this.authorDraft = '';
    this.pendingCoverFile = null;
    this.form.reset({
      title: book.title,
      priceAmount:
        book.priceAmount != null ? roundMoney2(Number(book.priceAmount)) : null,
      authorNames: book.authors.map((a) => a.name),
      categoryIds: book.categories.map((c) => c.id),
      coverUrl: book.coverUrl || '',
      description: book.description || '',
      isbn: book.isbn || '',
      pageCount: book.pageCount ?? null,
      initialStock: 0,
      version: book.version,
    });
    this.cdr.markForCheck();
  }

  /** Dosya seçiliyse OL coverUrl gövdede gönderilmez (sunucu ingest yerine multipart). */
  private toCreateBody(omitCoverUrl: boolean): CreateBookRequest {
    const body = buildCreateBookBody(this.form.getRawValue());
    if (omitCoverUrl) {
      delete body.coverUrl;
    }
    return body;
  }

  private toUpdateBody(): UpdateBookRequest {
    return buildUpdateBookBody(this.form.getRawValue(), {
      previousIsbn: this.loadedIsbn,
    });
  }

  private loadLookups(): Observable<void> {
    return this.loadAll((page, size) => this.api.listCategories({ page, size })).pipe(
      map((categories) => {
        this.categories = categories;
      }),
    );
  }

  private loadAll<T>(
    fetch: (page: number, size: number) => Observable<PageResponse<T>>,
  ): Observable<T[]> {
    return fetch(0, LOOKUP_SIZE).pipe(
      switchMap((first) => {
        if (first.totalPages <= 1) {
          return of(first.items);
        }
        const rest: Observable<PageResponse<T>>[] = [];
        for (let p = 1; p < first.totalPages; p += 1) {
          rest.push(fetch(p, LOOKUP_SIZE));
        }
        return forkJoin(rest).pipe(
          map((pages) => first.items.concat(...pages.map((page) => page.items))),
        );
      }),
    );
  }
}
