import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { EMPTY, merge, Subject, timer } from 'rxjs';
import { catchError, finalize, switchMap, takeUntil, takeWhile, tap } from 'rxjs/operators';
import { OrderApi } from '../../../core/api/order.api';
import { CartStore } from '../../../core/cart/cart.store';
import { messageForErrorCode } from '../../../core/interceptors/error-messages';
import { toProblemDetail } from '../../../core/interceptors/error.interceptor';
import { OrderItemResponse, OrderResponse } from '../../../core/models';

const POLL_INTERVAL_MS = 2000;
const POLL_MAX_MS = 60_000;

type PageState = 'loading' | 'ready' | 'notFound' | 'error';

@Component({
  selector: 'app-order-detail-page',
  templateUrl: './order-detail-page.component.html',
  styleUrls: ['./order-detail-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderDetailPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  pageState: PageState = 'loading';
  order: OrderResponse | null = null;
  pollTimedOut = false;
  failureMessage: string | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly orderApi: OrderApi,
    private readonly cartStore: CartStore,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        takeUntil(this.destroy$),
        switchMap((params) => {
          const id = params.get('id');
          this.order = null;
          this.pollTimedOut = false;
          this.failureMessage = null;

          if (!id) {
            this.pageState = 'notFound';
            this.cdr.markForCheck();
            return EMPTY;
          }

          this.pageState = 'loading';
          this.cdr.markForCheck();

          let hitTimeout = false;
          const stopPoll$ = new Subject<void>();

          return timer(0, POLL_INTERVAL_MS).pipe(
            takeUntil(
              merge(
                this.destroy$,
                stopPoll$,
                timer(POLL_MAX_MS).pipe(
                  tap(() => {
                    hitTimeout = true;
                  }),
                ),
              ),
            ),
            switchMap(() =>
              this.orderApi.getById(id).pipe(
                catchError((err: unknown) => {
                  const problem = toProblemDetail(err);
                  if (problem.status === 404) {
                    this.pageState = 'notFound';
                  } else {
                    this.pageState = 'error';
                  }
                  this.cdr.markForCheck();
                  stopPoll$.next();
                  stopPoll$.complete();
                  return EMPTY;
                }),
              ),
            ),
            takeWhile((order) => order.status === 'pending', true),
            finalize(() => {
              if (
                hitTimeout &&
                this.pageState === 'ready' &&
                this.order?.status === 'pending'
              ) {
                this.pollTimedOut = true;
                this.cdr.markForCheck();
              }
            }),
          );
        }),
      )
      .subscribe((order) => {
        this.applyOrder(order);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  trackByBookId(_index: number, item: OrderItemResponse): string {
    return item.bookId;
  }

  addressSummary(order: OrderResponse): string {
    const a = order.address;
    const parts = [a.line1, a.line2, a.district, a.city, a.postalCode, a.country].filter(
      (p): p is string => !!p && p.trim().length > 0,
    );
    return parts.join(', ');
  }

  private applyOrder(order: OrderResponse): void {
    const prevStatus = this.order?.status;
    this.order = order;
    this.pageState = 'ready';

    if (order.status === 'failed') {
      this.failureMessage =
        messageForErrorCode(order.failureCode ?? undefined) ??
        order.failureCode ??
        'Ödeme tamamlanamadı.';
    } else {
      this.failureMessage = null;
    }

    if (order.status === 'paid' && prevStatus !== 'paid') {
      this.cartStore.load().subscribe({ error: () => undefined });
    }

    if (order.status !== 'pending') {
      this.pollTimedOut = false;
    }

    this.cdr.markForCheck();
  }
}
