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
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { forkJoin, Observable, of, Subject } from 'rxjs';
import { finalize, map, switchMap, takeUntil } from 'rxjs/operators';
import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import { toProblemDetail } from '../../../core/interceptors/error.interceptor';
import { CategoryResponse, FieldError, PageResponse } from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const PAGE_SIZE = 100;

type PageState = 'loading' | 'ready' | 'empty' | 'error';

@Component({
  selector: 'app-admin-categories-page',
  templateUrl: './admin-categories-page.component.html',
  styleUrls: ['./admin-categories-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminCategoriesPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private modalRef: NgbModalRef | null = null;

  @ViewChild('entityModal') entityModal!: TemplateRef<unknown>;

  pageState: PageState = 'loading';
  items: CategoryResponse[] = [];

  editing: CategoryResponse | null = null;
  saving = false;
  busyId: string | null = null;
  fieldErrors: FieldError[] = [];

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    slug: ['', [Validators.pattern(SLUG_PATTERN), Validators.maxLength(120)]],
  });

  constructor(
    private readonly api: AdminCatalogApi,
    private readonly fb: FormBuilder,
    private readonly modal: NgbModal,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly toast: ToastService,
    private readonly cdr: ChangeDetectorRef,
  ) {
    this.form.statusChanges.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.cdr.markForCheck();
    });
  }

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.modalRef?.dismiss();
    this.destroy$.next();
    this.destroy$.complete();
  }

  trackById(_index: number, item: CategoryResponse): string {
    return item.id;
  }

  get canSave(): boolean {
    return this.form.valid && !this.saving;
  }

  openCreate(): void {
    this.editing = null;
    this.fieldErrors = [];
    this.form.reset({
      name: '',
      slug: '',
    });
    this.openModal();
  }

  openEdit(item: CategoryResponse): void {
    this.editing = item;
    this.fieldErrors = [];
    this.form.reset({
      name: item.name,
      slug: item.slug,
    });
    this.openModal();
  }

  onFormCancel(): void {
    this.modalRef?.dismiss();
  }

  onFormSubmit(): void {
    if (!this.canSave) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    this.fieldErrors = [];
    this.cdr.markForCheck();

    const raw = this.form.getRawValue() as { name: string; slug: string };
    const name = raw.name.trim();
    const slug = raw.slug?.trim() || undefined;

    const request$ = this.editing
      ? this.api.updateCategory(this.editing.id, { name, slug })
      : this.api.createCategory({ name, ...(slug ? { slug } : {}) });

    request$
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.toast.success(this.editing ? 'Kategori güncellendi' : 'Kategori eklendi');
          this.modalRef?.close();
          this.load();
        },
        error: (err: unknown) => {
          const problem = toProblemDetail(err);
          this.fieldErrors = problem.errors ?? [];
          this.cdr.markForCheck();
        },
      });
  }

  onDelete(item: CategoryResponse): void {
    if (this.busyId) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Kategoriyi sil',
        message: `"${item.name}" silinecek. Emin misiniz?`,
        confirmLabel: 'Sil',
        cancelLabel: 'Vazgeç',
        confirmButtonClass: 'btn-danger',
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((ok) => {
        if (!ok) {
          return;
        }
        this.busyId = item.id;
        this.cdr.markForCheck();
        this.api
          .deleteCategory(item.id)
          .pipe(
            takeUntil(this.destroy$),
            finalize(() => {
              this.busyId = null;
              this.cdr.markForCheck();
            }),
          )
          .subscribe({
            next: () => {
              this.toast.success('Kategori silindi');
              this.load();
            },
            error: () => undefined,
          });
      });
  }

  private load(): void {
    this.pageState = 'loading';
    this.cdr.markForCheck();
    this.loadAll((page, size) => this.api.listCategories({ page, size }))
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (items) => {
          this.items = items;
          this.pageState = items.length === 0 ? 'empty' : 'ready';
          this.cdr.markForCheck();
        },
        error: () => {
          this.pageState = 'error';
          this.cdr.markForCheck();
        },
      });
  }

  private loadAll(
    fetch: (page: number, size: number) => Observable<PageResponse<CategoryResponse>>,
  ): Observable<CategoryResponse[]> {
    return fetch(0, PAGE_SIZE).pipe(
      switchMap((first) => {
        if (first.totalPages <= 1) {
          return of(first.items);
        }
        const rest: Observable<PageResponse<CategoryResponse>>[] = [];
        for (let p = 1; p < first.totalPages; p += 1) {
          rest.push(fetch(p, PAGE_SIZE));
        }
        return forkJoin(rest).pipe(
          map((pages) => first.items.concat(...pages.map((page) => page.items))),
        );
      }),
    );
  }

  private openModal(): void {
    if (!this.entityModal) {
      return;
    }
    this.modalRef?.dismiss();
    this.modalRef = this.modal.open(this.entityModal, {
      centered: true,
      backdrop: 'static',
    });
    this.modalRef.result.finally(() => {
      this.modalRef = null;
      this.saving = false;
      this.fieldErrors = [];
      this.editing = null;
      this.cdr.markForCheck();
    });
    this.cdr.markForCheck();
  }
}
