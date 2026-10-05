import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AddressResponse, UserResponse } from '../models';
import { AccountApi } from './account.api';

describe('AccountApi', () => {
  let api: AccountApi;
  let httpMock: HttpTestingController;

  const user: UserResponse = {
    id: 'u1',
    email: 'a@b.com',
    firstName: 'Ali',
    lastName: 'Veli',
    phone: '555',
    role: 'USER',
    status: 'ACTIVE',
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

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    api = TestBed.inject(AccountApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('updateProfile should PATCH /api/me', () => {
    api.updateProfile({ firstName: 'Ayşe', phone: '' }).subscribe((res) => {
      expect(res).toEqual({ ...user, firstName: 'Ayşe', phone: null });
    });
    const req = httpMock.expectOne('/api/me');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ firstName: 'Ayşe', phone: '' });
    req.flush({ ...user, firstName: 'Ayşe', phone: null });
  });

  it('listAddresses should GET /api/me/addresses', () => {
    api.listAddresses().subscribe((res) => expect(res).toEqual([address]));
    const req = httpMock.expectOne('/api/me/addresses');
    expect(req.request.method).toBe('GET');
    req.flush([address]);
  });

  it('createAddress should POST /api/me/addresses', () => {
    api
      .createAddress({
        recipientName: 'Ali Veli',
        phone: '555',
        line1: 'Cadde 1',
        city: 'İstanbul',
      })
      .subscribe();
    const req = httpMock.expectOne('/api/me/addresses');
    expect(req.request.method).toBe('POST');
    req.flush(address);
  });

  it('updateAddress should PATCH /api/me/addresses/:id', () => {
    api.updateAddress('a1', { city: 'Ankara' }).subscribe();
    const req = httpMock.expectOne('/api/me/addresses/a1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ city: 'Ankara' });
    req.flush({ ...address, city: 'Ankara' });
  });

  it('deleteAddress should DELETE /api/me/addresses/:id', () => {
    api.deleteAddress('a1').subscribe();
    const req = httpMock.expectOne('/api/me/addresses/a1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('setDefaultAddress should PATCH isDefault true', () => {
    api.setDefaultAddress('a1').subscribe();
    const req = httpMock.expectOne('/api/me/addresses/a1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ isDefault: true });
    req.flush(address);
  });

  it('getAddress should GET /api/me/addresses/:id', () => {
    api.getAddress('a1').subscribe((res) => expect(res).toEqual(address));
    const req = httpMock.expectOne('/api/me/addresses/a1');
    expect(req.request.method).toBe('GET');
    req.flush(address);
  });
});
