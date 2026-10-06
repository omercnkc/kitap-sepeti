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
import { Subject } from 'rxjs';
import { finalize, switchMap, takeUntil } from 'rxjs/operators';
import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import {
  AdminBook,
  AdminBookStatus,
  AdminBookSummary,
  PageResponse,
} from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 20;

type PageState = 'loading' | 'ready' | 'empty' | 'error';

interface StatusOption {
  value: '' | AdminBookStatus;
  label: string;
}

@Component({
  selector: 'app-admin-books-page',
  templateUrl: './admin-books-page.component.html',
  styleUrls: ['./admin-books-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminBooksPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private stockModalRef: NgbModalRef | null = null;

  @ViewChild('stockModal') stockModal!: TemplateRef<unknown>;

  readonly statusOptions: StatusOption[] = [
    { value: '', label: 'Tümü' },
    { value: 'draft', label: 'Taslak' },
    { value: 'published', label: 'Yayında' },
    { value: 'archived', label: 'Arşiv' },
  ];

  pageState: PageState = 'loading';
  items: AdminBookSummary[] = [];
  pageIndex = DEFAULT_PAGE;
  pageSize = DEFAULT_SIZE;
  totalElements = 0;
  totalPages = 0;
  statusFilter: '' | AdminBookStatus = '';
  busyId: string | null = null;
  stockTarget: AdminBookSummary | null = null;
  stockSaving = false;

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
          const status = parseStatus(params.get('status'));
          this.pageIndex = page;
          this.statusFilter = status;
          this.pageState = 'loading';
          this.cdr.markForCheck();
          return this.api
            .listBooks({
              page,
              size: this.pageSize,
              status: status || null,
            })
            .pipe(finalize(() => this.cdr.markForCheck()));
        }),
      )
      .subscribe({
        next: (page) => this.applyPage(page),
        error: () => {
          this.pageState = 'error';
          this.cdr.markForCheck();
        },
      });
  }

  ngOnDestroy(): void {
    this.stockModalRef?.dismiss();
    this.destroy$.next();
    this.destroy$.complete();
  }

  trackById(_index: number, item: AdminBookSummary): string {
    return item.id;
  }

  canPublish(status: AdminBookStatus): boolean {
    return status === 'draft' || status === 'archived';
  }

  canArchive(status: AdminBookStatus): boolean {
    return status === 'published';
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

  statusBadgeClass(status: AdminBookStatus): string {
    switch (status) {
      case 'published':
        return 'bg-success';
      case 'archived':
        return 'bg-secondary';
      default:
        return 'bg-warning text-dark';
    }
  }

  onStatusChange(value: string): void {
    const status = parseStatus(value);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        status: status || null,
        page: null,
      },
      queryParamsHandling: 'merge',
    });
  }

  onPageIndexChange(pageIndex: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: pageIndex || null },
      queryParamsHandling: 'merge',
    });
  }

  openCreate(): void {
    void this.router.navigate(['/admin/books/new']);
  }

  openEdit(item: AdminBookSummary): void {
    void this.router.navigate(['/admin/books', item.id]);
  }

  onPublish(item: AdminBookSummary): void {
    if (this.busyId || !this.canPublish(item.status)) {
      return;
    }
    const previous = item.status;
    this.busyId = item.id;
    this.cdr.markForCheck();
    this.api
      .publishBook(item.id)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.busyId = null;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (book) => {
          if (book.status !== previous) {
            this.toast.success('Kitap yayınlandı');
          }
          this.afterLifecycle(book);
        },
        error: () => undefined,
      });
  }

  onArchive(item: AdminBookSummary): void {
    if (this.busyId || !this.canArchive(item.status)) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Kitabı arşivle',
        message: `"${item.title}" arşivlenecek ve vitrinden kalkacak. Emin misiniz?`,
        confirmLabel: 'Arşivle',
        cancelLabel: 'Vazgeç',
        confirmButtonClass: 'btn-warning',
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((ok) => {
        if (!ok) {
          return;
        }
        const previous = item.status;
        this.busyId = item.id;
        this.cdr.markForCheck();
        this.api
          .archiveBook(item.id)
          .pipe(
            takeUntil(this.destroy$),
            finalize(() => {
              this.busyId = null;
              this.cdr.markForCheck();
            }),
          )
          .subscribe({
            next: (book) => {
              if (book.status !== previous) {
                this.toast.success('Kitap arşivlendi');
              }
              this.afterLifecycle(book);
            },
            error: () => undefined,
          });
      });
  }

  openStockAdjust(item: AdminBookSummary): void {
    if (this.busyId || !this.stockModal) {
      return;
    }
    this.stockTarget = item;
    this.stockForm.reset({ delta: null });
    this.stockModalRef?.dismiss();
    this.stockModalRef = this.modal.open(this.stockModal, {
      centered: true,
      backdrop: 'static',
    });
    this.stockModalRef.result.finally(() => {
      this.stockModalRef = null;
      this.stockTarget = null;
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
    if (!this.canSubmitStock || !this.stockTarget) {
      return;
    }
    const delta = Number(this.stockForm.get('delta')?.value);
    const targetId = this.stockTarget.id;
    this.stockSaving = true;
    this.busyId = targetId;
    this.cdr.markForCheck();
    this.api
      .adjustBookStock(targetId, { delta })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.stockSaving = false;
          this.busyId = null;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (book) => {
          this.toast.success('Stok güncellendi');
          this.stockModalRef?.close();
          this.afterLifecycle(book);
        },
        error: () => undefined,
      });
  }

  private afterLifecycle(book: AdminBook): void {
    if (this.statusFilter && book.status !== this.statusFilter) {
      this.reload();
      return;
    }
    const idx = this.items.findIndex((i) => i.id === book.id);
    if (idx < 0) {
      this.reload();
      return;
    }
    this.items = this.items.map((item, i) =>
      i === idx ? toSummary(book) : item,
    );
    this.cdr.markForCheck();
  }

  private reload(): void {
    this.api
      .listBooks({
        page: this.pageIndex,
        size: this.pageSize,
        status: this.statusFilter || null,
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (page) => this.applyPage(page),
        error: () => {
          this.pageState = 'error';
          this.cdr.markForCheck();
        },
      });
  }

  private applyPage(page: PageResponse<AdminBookSummary>): void {
    this.items = page.items;
    this.pageIndex = page.page;
    this.pageSize = page.size;
    this.totalElements = page.totalElements;
    this.totalPages = page.totalPages;
    this.pageState = page.items.length === 0 ? 'empty' : 'ready';
    this.cdr.markForCheck();
  }
}

function toSummary(book: AdminBook): AdminBookSummary {
  return {
    id: book.id,
    title: book.title,
    priceAmount: book.priceAmount,
    currency: book.currency,
    status: book.status,
    stockQuantity: book.stockQuantity,
    reservedQuantity: book.reservedQuantity,
    availableQuantity: book.availableQuantity,
    updatedAt: book.updatedAt,
    version: book.version,
  };
}

function parsePage(raw: string | null, fallback: number): number {
  if (raw == null || raw === '') {
    return fallback;
  }
  const n = Number(raw);
  return Number.isInteger(n) && n >= 0 ? n : fallback;
}

function parseStatus(raw: string | null): '' | AdminBookStatus {
  if (raw === 'draft' || raw === 'published' || raw === 'archived') {
    return raw;
  }
  return '';
}
