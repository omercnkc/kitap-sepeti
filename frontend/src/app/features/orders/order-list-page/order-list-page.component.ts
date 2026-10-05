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
import { OrderApi } from '../../../core/api/order.api';
import { OrderStatus, OrderSummaryResponse, PageResponse } from '../../../core/models';

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 20;

type PageState = 'loading' | 'ready' | 'empty' | 'error';

@Component({
  selector: 'app-order-list-page',
  templateUrl: './order-list-page.component.html',
  styleUrls: ['./order-list-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderListPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  pageState: PageState = 'loading';
  orders: OrderSummaryResponse[] = [];
  pageIndex = DEFAULT_PAGE;
  pageSize = DEFAULT_SIZE;
  totalElements = 0;
  totalPages = 0;

  constructor(
    private readonly orderApi: OrderApi,
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
          const size = parseSize(params.get('size'), DEFAULT_SIZE);
          this.pageIndex = page;
          this.pageSize = size;
          this.pageState = 'loading';
          this.cdr.markForCheck();

          return this.orderApi.list({ page, size }).pipe(
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
    this.destroy$.next();
    this.destroy$.complete();
  }

  trackById(_index: number, order: OrderSummaryResponse): string {
    return order.id;
  }

  onPageIndexChange(pageIndex: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: pageIndex },
      queryParamsHandling: 'merge',
    });
  }

  openOrder(order: OrderSummaryResponse): void {
    void this.router.navigate(['/orders', order.id]);
  }

  statusBadgeClass(status: OrderStatus): string {
    switch (status) {
      case 'paid':
        return 'bg-success';
      case 'failed':
        return 'bg-danger';
      case 'cancelled':
        return 'bg-secondary';
      case 'pending':
      default:
        return 'bg-warning text-dark';
    }
  }

  private applyPage(page: PageResponse<OrderSummaryResponse>): void {
    this.orders = page.items;
    this.pageIndex = page.page;
    this.pageSize = page.size;
    this.totalElements = page.totalElements;
    this.totalPages = page.totalPages;
    this.pageState = page.items.length === 0 ? 'empty' : 'ready';
    this.cdr.markForCheck();
  }
}

function parsePage(raw: string | null, fallback: number): number {
  if (raw === null || raw.trim() === '') {
    return fallback;
  }
  const n = Number(raw);
  if (!Number.isFinite(n) || n < 0) {
    return fallback;
  }
  return Math.floor(n);
}

function parseSize(raw: string | null, fallback: number): number {
  if (raw === null || raw.trim() === '') {
    return fallback;
  }
  const n = Number(raw);
  if (!Number.isFinite(n) || n < 1 || n > 50) {
    return fallback;
  }
  return Math.floor(n);
}
