import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { AccountApi } from '../../../core/api/account.api';
import { OrderApi } from '../../../core/api/order.api';
import { CartStore } from '../../../core/cart/cart.store';
import { AddressResponse, CartResponse, OrderResponse } from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { SharedModule } from '../../../shared/shared.module';
import { CheckoutPageComponent } from './checkout-page.component';

describe('CheckoutPageComponent', () => {
  let fixture: ComponentFixture<CheckoutPageComponent>;
  let cart$: BehaviorSubject<CartResponse | null>;
  let loadCartSpy: jasmine.Spy;
  let listAddressesSpy: jasmine.Spy;
  let createAddressSpy: jasmine.Spy;
  let checkoutSpy: jasmine.Spy;
  let router: Router;

  const cart: CartResponse = {
    catalogStatus: 'VERIFIED',
    currency: 'TRY',
    itemCount: 2,
    lineCount: 1,
    subtotal: 100,
    items: [
      {
        bookId: 'b1',
        title: 'Deneme',
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

  const address: AddressResponse = {
    id: 'a1',
    label: 'Ev',
    recipientName: 'Ali Veli',
    phone: '5551112233',
    line1: 'Cadde 1',
    line2: null,
    district: 'Kadıköy',
    city: 'İstanbul',
    postalCode: '34710',
    country: 'TR',
    isDefault: true,
    createdAt: '2026-01-01T00:00:00Z',
  };

  const order: OrderResponse = {
    id: 'o1',
    status: 'pending',
    currency: 'TRY',
    subtotal: 100,
    discountAmount: 0,
    totalAmount: 100,
    failureCode: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    address: {
      recipientName: 'Ali Veli',
      phone: '5551112233',
      line1: 'Cadde 1',
      line2: null,
      district: 'Kadıköy',
      city: 'İstanbul',
      postalCode: '34710',
      country: 'TR',
    },
    items: [
      {
        bookId: 'b1',
        title: 'Deneme',
        quantity: 2,
        unitPrice: 50,
        lineTotal: 100,
      },
    ],
  };

  beforeEach(async () => {
    cart$ = new BehaviorSubject<CartResponse | null>(cart);
    loadCartSpy = jasmine.createSpy('load').and.returnValue(of(cart));
    listAddressesSpy = jasmine.createSpy('listAddresses').and.returnValue(of([address]));
    createAddressSpy = jasmine.createSpy('createAddress').and.returnValue(of(address));
    checkoutSpy = jasmine.createSpy('checkout').and.returnValue(of(order));

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, SharedModule],
      declarations: [CheckoutPageComponent],
      providers: [
        {
          provide: CartStore,
          useValue: {
            cart$: cart$.asObservable(),
            load: loadCartSpy,
            reset: jasmine.createSpy('reset'),
          },
        },
        {
          provide: AccountApi,
          useValue: {
            listAddresses: listAddressesSpy,
            createAddress: createAddressSpy,
          },
        },
        {
          provide: OrderApi,
          useValue: { checkout: checkoutSpy },
        },
        {
          provide: ToastService,
          useValue: { success: jasmine.createSpy('success') },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(CheckoutPageComponent);
    fixture.detectChanges();
  });

  it('loads cart and addresses, selects default', () => {
    expect(loadCartSpy).toHaveBeenCalled();
    expect(listAddressesSpy).toHaveBeenCalled();
    expect(fixture.componentInstance.selectedAddressId).toBe('a1');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Deneme');
    expect(el.textContent).toContain('Ev');
  });

  it('redirects when cart is empty', () => {
    loadCartSpy.and.returnValue(
      of({
        ...cart,
        itemCount: 0,
        lineCount: 0,
        items: [],
        subtotal: 0,
      }),
    );
    const emptyFixture = TestBed.createComponent(CheckoutPageComponent);
    emptyFixture.detectChanges();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/cart');
  });

  it('confirms checkout and navigates to order detail', () => {
    fixture.componentInstance.onConfirm();
    expect(checkoutSpy).toHaveBeenCalledWith({
      address: jasmine.objectContaining({
        recipientName: 'Ali Veli',
        city: 'İstanbul',
        country: 'TR',
      }),
    });
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 'o1']);
  });

  it('ignores double confirm while submitting', () => {
    checkoutSpy.and.returnValue(of(order));
    fixture.componentInstance.submitting = true;
    fixture.componentInstance.onConfirm();
    expect(checkoutSpy).not.toHaveBeenCalled();
  });

  it('shows pending order link on ORDER_PENDING_EXISTS', () => {
    checkoutSpy.and.returnValue(
      throwError(() => ({
        title: 'Conflict',
        status: 409,
        code: 'ORDER_PENDING_EXISTS',
        orderId: 'pending-1',
      })),
    );
    fixture.componentInstance.onConfirm();
    fixture.detectChanges();
    expect(fixture.componentInstance.pendingOrderId).toBe('pending-1');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Bekleyen');
    expect(el.querySelector('a[href="/orders/pending-1"], a[ng-reflect-router-link]') || el.textContent).toBeTruthy();
  });

  it('saves new address then selects it', () => {
    listAddressesSpy.and.returnValue(of([address]));
    fixture.componentInstance.openNewAddressForm();
    fixture.componentInstance.onNewAddressSave({
      recipientName: 'Ali Veli',
      phone: '555',
      line1: 'Cadde 1',
      city: 'İstanbul',
      country: 'TR',
      isDefault: true,
    });
    expect(createAddressSpy).toHaveBeenCalled();
    expect(fixture.componentInstance.selectedAddressId).toBe('a1');
    expect(fixture.componentInstance.showNewAddressForm).toBeFalse();
  });
});
