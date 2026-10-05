import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { CartResponse, UserResponse } from '../models';
import { CartStore } from './cart.store';

describe('CartStore', () => {
  let store: CartStore;
  let httpMock: HttpTestingController;
  let currentUser$: BehaviorSubject<UserResponse | null>;

  const cart: CartResponse = {
    catalogStatus: 'VERIFIED',
    currency: 'TRY',
    itemCount: 2,
    lineCount: 1,
    subtotal: 100,
    items: [
      {
        bookId: 'b1',
        title: 'Kitap',
        coverUrl: null,
        quantity: 2,
        currency: 'TRY',
        snapshotUnitPrice: 50,
        currentUnitPrice: 50,
        lineTotal: 100,
        priceChanged: false,
        available: true,
      },
    ],
  };

  const user: UserResponse = {
    id: 'u1',
    email: 'a@b.com',
    firstName: 'Ali',
    lastName: 'Veli',
    phone: null,
    role: 'USER',
    status: 'ACTIVE',
  };

  beforeEach(() => {
    currentUser$ = new BehaviorSubject<UserResponse | null>(null);
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        CartStore,
        { provide: AuthService, useValue: { currentUser$ } },
      ],
    });
    store = TestBed.inject(CartStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads cart when user logs in and clears on logout', () => {
    let count = 0;
    store.count$.subscribe((c) => (count = c));

    currentUser$.next(user);
    httpMock.expectOne('/api/cart').flush(cart);
    expect(count).toBe(2);
    expect(store.snapshot?.itemCount).toBe(2);

    currentUser$.next(null);
    expect(store.snapshot).toBeNull();
    expect(count).toBe(0);
  });

  it('add updates cart state', () => {
    currentUser$.next(user);
    httpMock.expectOne('/api/cart').flush({
      ...cart,
      itemCount: 0,
      lineCount: 0,
      items: [],
      subtotal: 0,
    });

    store.add('b1', 1).subscribe();
    const req = httpMock.expectOne('/api/cart/items');
    expect(req.request.body).toEqual({ bookId: 'b1', quantity: 1 });
    req.flush(cart);
    expect(store.snapshot?.itemCount).toBe(2);
  });
});
