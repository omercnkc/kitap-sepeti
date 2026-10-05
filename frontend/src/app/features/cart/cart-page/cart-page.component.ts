import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import { CartStore } from '../../../core/cart/cart.store';
import { CartLineResponse, CartResponse } from '../../../core/models';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

const MAX_LINE_QTY = 99;

@Component({
  selector: 'app-cart-page',
  templateUrl: './cart-page.component.html',
  styleUrls: ['./cart-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CartPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  readonly cart$: Observable<CartResponse | null> = this.cartStore.cart$;

  loading = true;
  busyBookId: string | null = null;
  clearing = false;

  constructor(
    private readonly cartStore: CartStore,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.cartStore
      .load()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.loading = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({ error: () => undefined });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  isEmpty(cart: CartResponse | null): boolean {
    return !cart || cart.itemCount === 0 || cart.items.length === 0;
  }

  isBusy(bookId?: string): boolean {
    if (this.clearing) {
      return true;
    }
    if (!bookId) {
      return this.busyBookId !== null;
    }
    return this.busyBookId === bookId;
  }

  unitPrice(line: CartLineResponse): number {
    return line.currentUnitPrice ?? line.snapshotUnitPrice;
  }

  canDecrement(line: CartLineResponse): boolean {
    return line.quantity > 1 && !this.isBusy(line.bookId);
  }

  canIncrement(line: CartLineResponse): boolean {
    return line.quantity < MAX_LINE_QTY && !this.isBusy(line.bookId);
  }

  onDecrement(line: CartLineResponse): void {
    if (!this.canDecrement(line)) {
      return;
    }
    this.setQuantity(line.bookId, line.quantity - 1);
  }

  onIncrement(line: CartLineResponse): void {
    if (!this.canIncrement(line)) {
      return;
    }
    this.setQuantity(line.bookId, line.quantity + 1);
  }

  onRemove(line: CartLineResponse): void {
    if (this.isBusy(line.bookId)) {
      return;
    }
    this.busyBookId = line.bookId;
    this.cdr.markForCheck();
    this.cartStore
      .remove(line.bookId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.busyBookId = null;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({ error: () => undefined });
  }

  onClear(): void {
    if (this.clearing) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Sepeti boşalt',
        message: 'Sepetteki tüm ürünler kaldırılacak. Emin misiniz?',
        confirmLabel: 'Sepeti boşalt',
        cancelLabel: 'Vazgeç',
        confirmButtonClass: 'btn-danger',
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((ok) => {
        if (!ok) {
          return;
        }
        this.clearing = true;
        this.cdr.markForCheck();
        this.cartStore
          .clear()
          .pipe(
            takeUntil(this.destroy$),
            finalize(() => {
              this.clearing = false;
              this.cdr.markForCheck();
            }),
          )
          .subscribe({ error: () => undefined });
      });
  }

  trackByBookId(_index: number, line: CartLineResponse): string {
    return line.bookId;
  }

  private setQuantity(bookId: string, quantity: number): void {
    this.busyBookId = bookId;
    this.cdr.markForCheck();
    this.cartStore
      .updateQuantity(bookId, quantity)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.busyBookId = null;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({ error: () => undefined });
  }
}
