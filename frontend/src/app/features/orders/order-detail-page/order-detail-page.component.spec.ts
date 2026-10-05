import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { OrderApi } from '../../../core/api/order.api';
import { CartStore } from '../../../core/cart/cart.store';
import { OrderResponse } from '../../../core/models';
import { SharedModule } from '../../../shared/shared.module';
import { OrderDetailPageComponent } from './order-detail-page.component';

describe('OrderDetailPageComponent', () => {
  let fixture: ComponentFixture<OrderDetailPageComponent>;
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let getByIdSpy: jasmine.Spy;
  let loadCartSpy: jasmine.Spy;

  const pendingOrder: OrderResponse = {
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
      phone: '555',
      line1: 'Cadde 1',
      line2: null,
      district: null,
      city: 'İstanbul',
      postalCode: null,
      country: 'TR',
    },
    items: [
      {
        bookId: 'b1',
        title: 'Deneme',
        quantity: 1,
        unitPrice: 100,
        lineTotal: 100,
      },
    ],
  };

  const paidOrder: OrderResponse = { ...pendingOrder, status: 'paid' };
  const failedOrder: OrderResponse = {
    ...pendingOrder,
    status: 'failed',
    failureCode: 'INSUFFICIENT_STOCK',
  };

  beforeEach(async () => {
    paramMap$ = new BehaviorSubject(convertToParamMap({ id: 'o1' }));
    getByIdSpy = jasmine.createSpy('getById').and.returnValue(of(pendingOrder));
    loadCartSpy = jasmine.createSpy('load').and.returnValue(of(null));

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, SharedModule],
      declarations: [OrderDetailPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: { paramMap: paramMap$.asObservable() },
        },
        { provide: OrderApi, useValue: { getById: getByIdSpy } },
        { provide: CartStore, useValue: { load: loadCartSpy } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderDetailPageComponent);
  });

  it('polls while pending then shows paid and reloads cart', fakeAsync(() => {
    getByIdSpy.and.returnValues(of(pendingOrder), of(pendingOrder), of(paidOrder));
    fixture.detectChanges();

    tick(0);
    fixture.detectChanges();
    expect(fixture.componentInstance.order?.status).toBe('pending');
    expect(
      (fixture.nativeElement as HTMLElement).textContent,
    ).toContain('Ödeme işleniyor');

    tick(2000);
    fixture.detectChanges();
    expect(getByIdSpy).toHaveBeenCalledTimes(2);

    tick(2000);
    fixture.detectChanges();
    expect(fixture.componentInstance.order?.status).toBe('paid');
    expect(loadCartSpy).toHaveBeenCalled();
    const paidText = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(paidText).toContain('Ödeme başarılı');
    expect(paidText).toContain('Deneme');
    expect(paidText).toContain('Teslimat adresi');
    expect(paidText.toLowerCase()).toContain('birim');
    expect(paidText).toContain('Cadde 1');
    expect(paidText).toContain('Ara toplam');

    // polling should stop after paid
    const calls = getByIdSpy.calls.count();
    tick(10_000);
    expect(getByIdSpy.calls.count()).toBe(calls);
  }));

  it('stops polling on destroy', fakeAsync(() => {
    getByIdSpy.and.returnValue(of(pendingOrder));
    fixture.detectChanges();
    tick(0);
    const callsAfterFirst = getByIdSpy.calls.count();
    expect(callsAfterFirst).toBeGreaterThan(0);

    fixture.destroy();
    tick(10_000);
    expect(getByIdSpy.calls.count()).toBe(callsAfterFirst);
  }));

  it('shows failed message without reloading cart', fakeAsync(() => {
    getByIdSpy.and.returnValue(of(failedOrder));
    fixture.detectChanges();
    tick(0);
    fixture.detectChanges();

    expect(fixture.componentInstance.order?.status).toBe('failed');
    expect(loadCartSpy).not.toHaveBeenCalled();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Ödeme başarısız');
    expect(el.textContent).toContain('Tekrar dene');
  }));

  it('shows not found on 404', fakeAsync(() => {
    getByIdSpy.and.returnValue(
      throwError(() => ({
        title: 'Not Found',
        status: 404,
        code: 'RESOURCE_NOT_FOUND',
      })),
    );
    fixture.detectChanges();
    tick(0);
    fixture.detectChanges();
    expect(fixture.componentInstance.pageState).toBe('notFound');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Sipariş bulunamadı',
    );
  }));

  it('shows cancelled state', fakeAsync(() => {
    getByIdSpy.and.returnValue(of({ ...pendingOrder, status: 'cancelled' }));
    fixture.detectChanges();
    tick(0);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Sipariş iptal edildi',
    );
  }));
});
