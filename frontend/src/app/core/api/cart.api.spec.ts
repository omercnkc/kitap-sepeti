import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CartResponse } from '../models';
import { CartApi } from './cart.api';

describe('CartApi', () => {
  let api: CartApi;
  let httpMock: HttpTestingController;

  const emptyCart: CartResponse = {
    catalogStatus: 'VERIFIED',
    currency: null,
    itemCount: 0,
    lineCount: 0,
    subtotal: 0,
    items: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    api = TestBed.inject(CartApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getCart should GET /api/cart', () => {
    api.getCart().subscribe((res) => expect(res).toEqual(emptyCart));
    const req = httpMock.expectOne('/api/cart');
    expect(req.request.method).toBe('GET');
    req.flush(emptyCart);
  });

  it('addItem should POST /api/cart/items', () => {
    api.addItem({ bookId: 'b1', quantity: 1 }).subscribe();
    const req = httpMock.expectOne('/api/cart/items');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ bookId: 'b1', quantity: 1 });
    req.flush(emptyCart);
  });

  it('updateItem should PATCH /api/cart/items/:bookId', () => {
    api.updateItem('b1', { quantity: 3 }).subscribe();
    const req = httpMock.expectOne('/api/cart/items/b1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ quantity: 3 });
    req.flush(emptyCart);
  });

  it('removeItem should DELETE /api/cart/items/:bookId', () => {
    api.removeItem('b1').subscribe();
    const req = httpMock.expectOne('/api/cart/items/b1');
    expect(req.request.method).toBe('DELETE');
    req.flush(emptyCart);
  });

  it('clear should DELETE /api/cart/items', () => {
    api.clear().subscribe();
    const req = httpMock.expectOne('/api/cart/items');
    expect(req.request.method).toBe('DELETE');
    req.flush(emptyCart);
  });
});
