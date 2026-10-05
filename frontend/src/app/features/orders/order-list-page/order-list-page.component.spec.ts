import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { OrderApi } from '../../../core/api/order.api';
import { OrderSummaryResponse, PageResponse } from '../../../core/models';
import { SharedModule } from '../../../shared/shared.module';
import { OrderListPageComponent } from './order-list-page.component';

describe('OrderListPageComponent', () => {
  let fixture: ComponentFixture<OrderListPageComponent>;
  let queryParamMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let listSpy: jasmine.Spy;
  let router: Router;

  const orders: OrderSummaryResponse[] = [
    {
      id: 'o-new',
      status: 'paid',
      currency: 'TRY',
      totalAmount: 120,
      itemCount: 2,
      failureCode: null,
      createdAt: '2026-02-01T10:00:00Z',
      updatedAt: '2026-02-01T10:00:00Z',
    },
    {
      id: 'o-old',
      status: 'failed',
      currency: 'TRY',
      totalAmount: 50,
      itemCount: 1,
      failureCode: 'INSUFFICIENT_STOCK',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
    },
  ];

  const page: PageResponse<OrderSummaryResponse> = {
    items: orders,
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
  };

  beforeEach(async () => {
    queryParamMap$ = new BehaviorSubject(convertToParamMap({}));
    listSpy = jasmine.createSpy('list').and.returnValue(of(page));

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, SharedModule],
      declarations: [OrderListPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: queryParamMap$.asObservable(),
            snapshot: { queryParamMap: convertToParamMap({}) },
          },
        },
        { provide: OrderApi, useValue: { list: listSpy } },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(OrderListPageComponent);
    fixture.detectChanges();
  });

  it('lists orders from OrderApi', () => {
    expect(listSpy).toHaveBeenCalledWith({ page: 0, size: 20 });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('o-new');
    expect(el.textContent).toContain('Ödendi');
    expect(el.textContent).toContain('Başarısız');
  });

  it('shows empty state when no orders', () => {
    listSpy.and.returnValue(
      of({
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      }),
    );
    queryParamMap$.next(convertToParamMap({}));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Henüz sipariş yok',
    );
  });

  it('navigates to detail on row open', () => {
    fixture.componentInstance.openOrder(orders[0]);
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 'o-new']);
  });

  it('requests next page from query params', () => {
    listSpy.calls.reset();
    listSpy.and.returnValue(
      of({
        ...page,
        page: 1,
        totalPages: 2,
        totalElements: 25,
      }),
    );
    queryParamMap$.next(convertToParamMap({ page: '1' }));
    fixture.detectChanges();
    expect(listSpy).toHaveBeenCalledWith({ page: 1, size: 20 });
  });

  it('shows error empty state on failure', () => {
    listSpy.and.returnValue(throwError(() => ({ status: 500, title: 'Error' })));
    queryParamMap$.next(convertToParamMap({ page: '0' }));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Siparişler yüklenemedi',
    );
  });
});
