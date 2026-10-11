import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize, map, switchMap, takeUntil } from 'rxjs/operators';
import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import { toProblemDetail } from '../../../core/interceptors/error.interceptor';
import { AuthorResponse, FieldError, PageResponse } from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { AdminNameSlugValue } from '../admin-name-slug-form/admin-name-slug-form.component';

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 20;
const SEARCH_MIN_LEN = 2;

type PageState = 'loading' | 'ready' | 'empty' | 'error';

@Component({
  selector: 'app-admin-authors-page',
  templateUrl: './admin-authors-page.component.html',
  styleUrls: ['./admin-authors-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminAuthorsPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private modalRef: NgbModalRef | null = null;

  @ViewChild('entityModal') entityModal!: TemplateRef<unknown>;

  pageState: PageState = 'loading';
  items: AuthorResponse[] = [];
  pageIndex = DEFAULT_PAGE;
  pageSize = DEFAULT_SIZE;
  totalElements = 0;
  totalPages = 0;
  qFilter: string | null = null;

  editing: AuthorResponse | null = null;
  saving = false;
  busyId: string | null = null;
  fieldErrors: FieldError[] = [];

  readonly searchControl = new FormControl('');

  constructor(
    private readonly api: AdminCatalogApi,
    private readonly modal: NgbModal,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly toast: ToastService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(
        takeUntil(this.destroy$),
        switchMap((params) => {
          const page = parsePage(params.get('page'), DEFAULT_PAGE);
          const q = optionalSearchQuery(params.get('q'));
          this.pageIndex = page;
          this.qFilter = q;
          const committed = q ?? '';
          const current = (this.searchControl.value ?? '').trim();
          const controlCommitted = current.length >= SEARCH_MIN_LEN ? current : '';
          if (committed !== controlCommitted) {
            this.searchControl.setValue(committed, { emitEvent: false });
          }
          this.pageState = 'loading';
          this.cdr.markForCheck();
          return this.api.listAuthors({ page, size: this.pageSize, q }).pipe(
            finalize(() => this.cdr.markForCheck()),
          );
        }),
      )
      .subscribe({
        next: (page) => this.applyPage(page),
        error: () => {
          this.pageState = 'error';
          this.cdr.markForCheck();
        },
      });

    this.searchControl.valueChanges
      .pipe(
        debounceTime(300),
        map((raw) => (raw ?? '').trim()),
        distinctUntilChanged(),
        takeUntil(this.destroy$),
      )
      .subscribe((trimmed) => {
        const q = trimmed.length >= SEARCH_MIN_LEN ? trimmed : null;
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { q, page: 0 },
          queryParamsHandling: 'merge',
        });
      });
  }

  ngOnDestroy(): void {
    this.modalRef?.dismiss();
    this.destroy$.next();
    this.destroy$.complete();
  }

  trackById(_index: number, item: AuthorResponse): string {
    return item.id;
  }

  onPageIndexChange(pageIndex: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: pageIndex || null },
      queryParamsHandling: 'merge',
    });
  }

  openCreate(): void {
    this.editing = null;
    this.fieldErrors = [];
    this.openModal();
  }

  openEdit(item: AuthorResponse): void {
    this.editing = item;
    this.fieldErrors = [];
    this.openModal();
  }

  openBooks(item: AuthorResponse): void {
    void this.router.navigate(['/admin/books'], {
      queryParams: { authorId: item.id, page: 0 },
    });
  }

  onFormCancel(): void {
    this.modalRef?.dismiss();
  }

  onFormSave(value: AdminNameSlugValue): void {
    if (this.saving) {
      return;
    }
    this.saving = true;
    this.fieldErrors = [];
    this.cdr.markForCheck();

    const request$ = this.editing
      ? this.api.updateAuthor(this.editing.id, value)
      : this.api.createAuthor(value);

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
          this.toast.success(this.editing ? 'Yazar güncellendi' : 'Yazar eklendi');
          this.modalRef?.close();
          this.reload();
        },
        error: (err: unknown) => {
          const problem = toProblemDetail(err);
          this.fieldErrors = problem.errors ?? [];
          this.cdr.markForCheck();
        },
      });
  }

  onDelete(item: AuthorResponse): void {
    if (this.busyId) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Yazarı sil',
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
          .deleteAuthor(item.id)
          .pipe(
            takeUntil(this.destroy$),
            finalize(() => {
              this.busyId = null;
              this.cdr.markForCheck();
            }),
          )
          .subscribe({
            next: () => {
              this.toast.success('Yazar silindi');
              this.reload();
            },
            error: () => undefined,
          });
      });
  }

  private applyPage(page: PageResponse<AuthorResponse>): void {
    this.items = page.items;
    this.pageIndex = page.page;
    this.pageSize = page.size;
    this.totalElements = page.totalElements;
    this.totalPages = page.totalPages;
    this.pageState = page.items.length === 0 && page.totalElements === 0 ? 'empty' : 'ready';
    this.cdr.markForCheck();
  }

  private reload(): void {
    this.api
      .listAuthors({ page: this.pageIndex, size: this.pageSize, q: this.qFilter })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (page) => {
          if (page.items.length === 0 && page.page > 0 && page.totalElements > 0) {
            this.onPageIndexChange(page.page - 1);
            return;
          }
          this.applyPage(page);
        },
        error: () => {
          this.pageState = 'error';
          this.cdr.markForCheck();
        },
      });
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

function parsePage(raw: string | null, fallback: number): number {
  if (raw == null || raw === '') {
    return fallback;
  }
  const n = Number(raw);
  return Number.isInteger(n) && n >= 0 ? n : fallback;
}

function optionalSearchQuery(value: string | null): string | null {
  if (value === null) {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length >= SEARCH_MIN_LEN ? trimmed : null;
}
