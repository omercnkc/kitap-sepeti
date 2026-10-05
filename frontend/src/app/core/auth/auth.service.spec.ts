import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';
import { TokenResponse, UserResponse } from '../models';

describe('AuthService', () => {
  let auth: AuthService;
  let httpMock: HttpTestingController;
  let tokenStorage: TokenStorageService;

  const tokens: TokenResponse = {
    accessToken: 'access',
    refreshToken: 'refresh',
    tokenType: 'Bearer',
    expiresIn: 900,
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
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    auth = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    tokenStorage = TestBed.inject(TokenStorageService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('login stores tokens and fills currentUser$', () => {
    auth.login('a@b.com', 'Secret123', true).subscribe((u) => {
      expect(u.email).toBe('a@b.com');
    });

    const loginReq = httpMock.expectOne('/api/auth/login');
    expect(loginReq.request.method).toBe('POST');
    loginReq.flush(tokens);

    const meReq = httpMock.expectOne('/api/me');
    meReq.flush(user);

    expect(tokenStorage.getAccessToken()).toBe('access');
    expect(tokenStorage.getRefreshToken()).toBe('refresh');
    expect(tokenStorage.isRememberMe()).toBeTrue();
    expect(auth.currentUserSnapshot).toEqual(user);
  });

  it('register stores tokens with session refresh by default', () => {
    auth
      .register({
        email: 'a@b.com',
        password: 'Secret123',
        firstName: 'Ali',
        lastName: 'Veli',
      })
      .subscribe();

    httpMock.expectOne('/api/auth/register').flush(tokens);
    httpMock.expectOne('/api/me').flush(user);

    expect(sessionStorage.getItem('kitapsepeti.refreshToken')).toBe('refresh');
    expect(localStorage.getItem('kitapsepeti.refreshToken')).toBeNull();
  });

  it('logout clears storage and currentUser$', () => {
    tokenStorage.setAccessToken('a');
    tokenStorage.setRefreshToken('r', true);
    auth.logout();
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(tokenStorage.getRefreshToken()).toBeNull();
    expect(auth.currentUserSnapshot).toBeNull();
  });

  it('refresh rotates tokens keeping rememberMe', () => {
    tokenStorage.setRefreshToken('old-refresh', true);

    auth.refresh().subscribe((res) => {
      expect(res.accessToken).toBe('access');
    });

    const req = httpMock.expectOne('/api/auth/refresh');
    expect(req.request.body).toEqual({ refreshToken: 'old-refresh' });
    req.flush(tokens);

    expect(tokenStorage.getAccessToken()).toBe('access');
    expect(localStorage.getItem('kitapsepeti.refreshToken')).toBe('refresh');
  });

  it('loadCurrentUser updates currentUser$', () => {
    tokenStorage.setAccessToken('access');
    auth.loadCurrentUser().subscribe();
    httpMock.expectOne('/api/me').flush(user);
    expect(auth.currentUserSnapshot).toEqual(user);
  });

  it('storage event clears in-memory session when refresh removed in another tab', () => {
    tokenStorage.setAccessToken('access');
    tokenStorage.setRefreshToken('refresh', true);
    auth.loadCurrentUser().subscribe();
    httpMock.expectOne('/api/me').flush(user);
    expect(auth.currentUserSnapshot).toEqual(user);

    localStorage.removeItem('kitapsepeti.refreshToken');
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: 'kitapsepeti.refreshToken',
        oldValue: 'refresh',
        newValue: null,
        storageArea: localStorage,
      }),
    );

    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(auth.currentUserSnapshot).toBeNull();
  });
});
