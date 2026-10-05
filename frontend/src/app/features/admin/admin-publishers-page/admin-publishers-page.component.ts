import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { finalize, switchMap, takeUntil } from 'rxjs/operators';
import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import { toProblemDetail } from '../../../core/interceptors/error.interceptor';
import { FieldError, PageResponse, PublisherResponse } from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { AdminNameSlugValue } from '../admin-name-slug-form/admin-name-slug-form.component';

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 20;

type PageState = 'loading' | 'ready' | 'empty' | 'error';

@Component({
  selector: 'app-admin-publishers-page',
  templateUrl: './admin-publishers-page.component.html',
  styleUrls: ['./admin-publishers-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPublishersPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private modalRef: NgbModalRef | null = null;

  @ViewChild('entityModal') entityModal!: TemplateRef<unknown>;

  pageState: PageState = 'loading';
  items: PublisherResponse[] = [];
  pageIndex = DEFAULT_PAGE;
  pageSize = DEFAULT_SIZE;
  totalElements = 0;
  totalPages = 0;

  editing: PublisherResponse | null = null;
  saving = false;
  busyId: string | null = null;
  fieldErrors: FieldError[] = [];

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
          this.pageIndex = page;
          this.pageState = 'loading';
          this.cdr.markForCheck();
          return this.api.listPublishers({ page, size: this.pageSize }).pipe(
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
  }

  ngOnDestroy(): void {
    this.modalRef?.dismiss();
    this.destroy$.next();
    this.destroy$.complete();
  }

  trackById(_index: number, item: PublisherResponse): string {
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

  openEdit(item: PublisherResponse): void {
    this.editing = item;
    this.fieldErrors = [];
    this.openModal();
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
      ? this.api.updatePublisher(this.editing.id, value)
      : this.api.createPublisher(value);

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
          this.toast.success(this.editing ? 'Yayınevi güncellendi' : 'Yayınevi eklendi');
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

  onDelete(item: PublisherResponse): void {
    if (this.busyId) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Yayınevini sil',
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
          .deletePublisher(item.id)
          .pipe(
            takeUntil(this.destroy$),
            finalize(() => {
              this.busyId = null;
              this.cdr.markForCheck();
            }),
          )
          .subscribe({
            next: () => {
              this.toast.success('Yayınevi silindi');
              this.reload();
            },
            error: () => undefined,
          });
      });
  }

  private applyPage(page: PageResponse<PublisherResponse>): void {
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
      .listPublishers({ page: this.pageIndex, size: this.pageSize })
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
