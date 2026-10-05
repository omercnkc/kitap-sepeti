import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { distinctUntilChanged, map, tap } from 'rxjs/operators';
import { CartApi } from '../api/cart.api';
import { AuthService } from '../auth/auth.service';
import { CartResponse } from '../models';

@Injectable({ providedIn: 'root' })
export class CartStore {
  private readonly cartSubject = new BehaviorSubject<CartResponse | null>(null);

  readonly cart$: Observable<CartResponse | null> = this.cartSubject.asObservable();

  readonly count$: Observable<number> = this.cart$.pipe(
    map((cart) => cart?.itemCount ?? 0),
    distinctUntilChanged(),
  );

  readonly subtotal$: Observable<number | null> = this.cart$.pipe(
    map((cart) => (cart ? cart.subtotal : null)),
    distinctUntilChanged(),
  );

  constructor(
    private readonly cartApi: CartApi,
    private readonly auth: AuthService,
  ) {
    this.auth.currentUser$
      .pipe(
        map((user) => user?.id ?? null),
        distinctUntilChanged(),
      )
      .subscribe((userId) => {
        if (userId) {
          this.load().subscribe({ error: () => this.reset() });
        } else {
          this.reset();
        }
      });
  }

  get snapshot(): CartResponse | null {
    return this.cartSubject.value;
  }

  load(): Observable<CartResponse> {
    return this.cartApi.getCart().pipe(tap((cart) => this.cartSubject.next(cart)));
  }

  add(bookId: string, quantity = 1): Observable<CartResponse> {
    return this.cartApi.addItem({ bookId, quantity }).pipe(
      tap((cart) => this.cartSubject.next(cart)),
    );
  }

  updateQuantity(bookId: string, quantity: number): Observable<CartResponse> {
    return this.cartApi.updateItem(bookId, { quantity }).pipe(
      tap((cart) => this.cartSubject.next(cart)),
    );
  }

  remove(bookId: string): Observable<CartResponse> {
    return this.cartApi.removeItem(bookId).pipe(
      tap((cart) => this.cartSubject.next(cart)),
    );
  }

  clear(): Observable<CartResponse> {
    return this.cartApi.clear().pipe(tap((cart) => this.cartSubject.next(cart)));
  }

  reset(): void {
    this.cartSubject.next(null);
  }
}
