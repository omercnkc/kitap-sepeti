import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, switchMap, takeUntil } from 'rxjs/operators';
import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import {
  AdminBookStatus,
  AdminBookSummary,
  PageResponse,
} from '../../../core/models';

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

  constructor(
    private readonly api: AdminCatalogApi,
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
    this.destroy$.next();
    this.destroy$.complete();
  }

  trackById(_index: number, item: AdminBookSummary): string {
    return item.id;
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
