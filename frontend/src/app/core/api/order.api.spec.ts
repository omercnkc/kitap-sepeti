import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OrderResponse, OrderSummaryResponse, PageResponse } from '../models';
import { OrderApi } from './order.api';

describe('OrderApi', () => {
  let api: OrderApi;
  let httpMock: HttpTestingController;

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
      phone: '555',
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
        title: 'Kitap',
        quantity: 1,
        unitPrice: 100,
        lineTotal: 100,
      },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    api = TestBed.inject(OrderApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('checkout should POST /api/orders/checkout', () => {
    api
      .checkout({
        address: {
          recipientName: 'Ali Veli',
          phone: '555',
          line1: 'Cadde 1',
          city: 'İstanbul',
          country: 'TR',
        },
      })
      .subscribe((res) => expect(res).toEqual(order));

    const req = httpMock.expectOne('/api/orders/checkout');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.address.city).toBe('İstanbul');
    req.flush(order, { status: 201, statusText: 'Created' });
  });

  it('getById should GET /api/orders/:id', () => {
    api.getById('o1').subscribe((res) => expect(res.id).toBe('o1'));
    const req = httpMock.expectOne('/api/orders/o1');
    expect(req.request.method).toBe('GET');
    req.flush(order);
  });

  it('list should GET /api/orders with page/size', () => {
    const page: PageResponse<OrderSummaryResponse> = {
      items: [
        {
          id: 'o1',
          status: 'pending',
          currency: 'TRY',
          totalAmount: 100,
          itemCount: 1,
          failureCode: null,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };

    api.list({ page: 0, size: 20 }).subscribe((res) => expect(res).toEqual(page));
    const req = httpMock.expectOne((r) => r.url === '/api/orders');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush(page);
  });
});
