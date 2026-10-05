import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthApi } from './auth.api';
import { TokenResponse, UserResponse } from '../models';

describe('AuthApi', () => {
  let api: AuthApi;
  let httpMock: HttpTestingController;

  const tokens: TokenResponse = {
    accessToken: 'access',
    refreshToken: 'refresh',
    tokenType: 'Bearer',
    expiresIn: 900,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    api = TestBed.inject(AuthApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('register should POST /api/auth/register', () => {
    api
      .register({
        email: 'a@b.com',
        password: 'Secret123',
        firstName: 'Ali',
        lastName: 'Veli',
      })
      .subscribe((res) => expect(res).toEqual(tokens));

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.email).toBe('a@b.com');
    req.flush(tokens);
  });

  it('login should POST /api/auth/login', () => {
    api.login({ email: 'a@b.com', password: 'Secret123' }).subscribe((res) => {
      expect(res.accessToken).toBe('access');
    });

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush(tokens);
  });

  it('refresh should POST /api/auth/refresh', () => {
    api.refresh({ refreshToken: 'refresh' }).subscribe((res) => {
      expect(res.refreshToken).toBe('refresh');
    });

    const req = httpMock.expectOne('/api/auth/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refreshToken: 'refresh' });
    req.flush(tokens);
  });

  it('getMe should GET /api/me without setting Authorization (interceptor owns Bearer)', () => {
    const user = {
      id: 'u1',
      email: 'a@b.com',
      firstName: 'Ali',
      lastName: 'Veli',
      phone: null,
      role: 'USER',
      status: 'ACTIVE',
    } as UserResponse;

    api.getMe().subscribe((res) => expect(res).toEqual(user));

    const req = httpMock.expectOne('/api/me');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush(user);
  });
});
