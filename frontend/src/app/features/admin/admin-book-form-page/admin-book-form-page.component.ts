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
  AuthorResponse,
  CategoryResponse,
  CreateBookRequest,
  FieldError,
  PageResponse,
  PublisherResponse,
  UpdateBookRequest,
} from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

const LOOKUP_SIZE = 100;

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

  @ViewChild('stockModal') stockModal!: TemplateRef<unknown>;

  pageState: PageState = 'loading';
  isCreate = true;
  bookId: string | null = null;
  book: AdminBook | null = null;
  saving = false;
  actionBusy = false;
  fieldErrors: FieldError[] = [];
  concurrentConflict = false;
  stockSaving = false;

  publishers: PublisherResponse[] = [];
  authors: AuthorResponse[] = [];
  categories: CategoryResponse[] = [];

  readonly form: FormGroup = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(300)]],
    publisherId: ['', [Validators.required]],
    priceAmount: [null as number | null, [Validators.required, Validators.min(0)]],
    authorIds: [[] as string[]],
    categoryIds: [[] as string[]],
    coverUrl: ['', [Validators.maxLength(500)]],
    description: ['', [Validators.maxLength(10000)]],
    isbn: [''],
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

  isAuthorSelected(id: string): boolean {
    return (this.form.get('authorIds')?.value as string[]).includes(id);
  }

  isCategorySelected(id: string): boolean {
    return (this.form.get('categoryIds')?.value as string[]).includes(id);
  }

  toggleAuthor(id: string, checked: boolean): void {
    this.toggleId('authorIds', id, checked);
  }

  toggleCategory(id: string, checked: boolean): void {
    this.toggleId('categoryIds', id, checked);
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

    const request$ = this.isCreate
      ? this.api.createBook(this.toCreateBody())
      : this.api.updateBook(this.bookId as string, this.toUpdateBody());

    request$
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (book) => {
          this.toast.success(this.isCreate ? 'Kitap oluşturuldu' : 'Kitap güncellendi');
          if (this.isCreate) {
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

  private toggleId(controlName: 'authorIds' | 'categoryIds', id: string, checked: boolean): void {
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

  private resetCreateForm(): void {
    this.form.reset({
      title: '',
      publisherId: '',
      priceAmount: null,
      authorIds: [],
      categoryIds: [],
      coverUrl: '',
      description: '',
      isbn: '',
      pageCount: null,
      initialStock: 0,
      version: null,
    });
  }

  private patchFromBook(book: AdminBook): void {
    this.book = book;
    this.form.reset({
      title: book.title,
      publisherId: book.publisher.id,
      priceAmount: book.priceAmount,
      authorIds: book.authors.map((a) => a.id),
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

  private toCreateBody(): CreateBookRequest {
    const raw = this.form.getRawValue();
    const body: CreateBookRequest = {
      title: String(raw.title).trim(),
      publisherId: raw.publisherId as string,
      priceAmount: Number(raw.priceAmount),
    };
    const authorIds = raw.authorIds as string[];
    const categoryIds = raw.categoryIds as string[];
    if (authorIds.length) {
      body.authorIds = authorIds;
    }
    if (categoryIds.length) {
      body.categoryIds = categoryIds;
    }
    const coverUrl = String(raw.coverUrl || '').trim();
    if (coverUrl) {
      body.coverUrl = coverUrl;
    }
    const description = String(raw.description || '').trim();
    if (description) {
      body.description = description;
    }
    const isbn = String(raw.isbn || '').trim();
    if (isbn) {
      body.isbn = isbn;
    }
    if (raw.pageCount != null && raw.pageCount !== '') {
      body.pageCount = Number(raw.pageCount);
    }
    if (raw.initialStock != null && raw.initialStock !== '') {
      body.initialStock = Number(raw.initialStock);
    }
    return body;
  }

  private toUpdateBody(): UpdateBookRequest {
    const raw = this.form.getRawValue();
    const body: UpdateBookRequest = {
      version: Number(raw.version),
      title: String(raw.title).trim(),
      publisherId: raw.publisherId as string,
      priceAmount: Number(raw.priceAmount),
      authorIds: raw.authorIds as string[],
      categoryIds: raw.categoryIds as string[],
    };
    body.coverUrl = String(raw.coverUrl || '').trim();
    body.description = String(raw.description || '').trim();
    body.isbn = String(raw.isbn || '').trim();
    if (raw.pageCount != null && raw.pageCount !== '') {
      body.pageCount = Number(raw.pageCount);
    }
    return body;
  }

  private loadLookups(): Observable<void> {
    return forkJoin({
      publishers: this.loadAll((page, size) => this.api.listPublishers({ page, size })),
      authors: this.loadAll((page, size) => this.api.listAuthors({ page, size })),
      categories: this.loadAll((page, size) => this.api.listCategories({ page, size })),
    }).pipe(
      map((result) => {
        this.publishers = result.publishers;
        this.authors = result.authors;
        this.categories = result.categories;
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
