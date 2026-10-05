import { HTTP_INTERCEPTORS, HttpClient } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { AuthService } from '../auth/auth.service';
import { TokenStorageService } from '../auth/token-storage.service';
import { AuthInterceptor } from './auth.interceptor';

describe('AuthInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let tokenStorage: TokenStorageService;
  let router: Router;
  let navigateSpy: jasmine.Spy;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([])],
      providers: [
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    tokenStorage = TestBed.inject(TokenStorageService);
    router = TestBed.inject(Router);
    navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    spyOnProperty(router, 'url', 'get').and.returnValue('/books');
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('adds Bearer access token to protected requests', () => {
    tokenStorage.setAccessToken('access-1');

    http.get('/api/me').subscribe();

    const req = httpMock.expectOne('/api/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush({ id: 'u1' });
  });

  it('does not add Bearer to public auth endpoints', () => {
    tokenStorage.setAccessToken('access-1');

    http.post('/api/auth/login', { email: 'a@b.com', password: 'x' }).subscribe();
    const login = httpMock.expectOne('/api/auth/login');
    expect(login.request.headers.has('Authorization')).toBeFalse();
    login.flush({
      accessToken: 'a',
      refreshToken: 'r',
      tokenType: 'Bearer',
      expiresIn: 900,
    });

    http.post('/api/auth/register', {}).subscribe();
    const register = httpMock.expectOne('/api/auth/register');
    expect(register.request.headers.has('Authorization')).toBeFalse();
    register.flush({
      accessToken: 'a',
      refreshToken: 'r',
      tokenType: 'Bearer',
      expiresIn: 900,
    });

    http.post('/api/auth/refresh', { refreshToken: 'r' }).subscribe();
    const refresh = httpMock.expectOne('/api/auth/refresh');
    expect(refresh.request.headers.has('Authorization')).toBeFalse();
    refresh.flush({
      accessToken: 'a',
      refreshToken: 'r2',
      tokenType: 'Bearer',
      expiresIn: 900,
    });
  });

  it('single-flights refresh for concurrent 401s and retries once', () => {
    tokenStorage.setAccessToken('old-access');
    tokenStorage.setRefreshToken('refresh-1', true);

    let okCount = 0;
    http.get('/api/me').subscribe(() => okCount++);
    http.get('/api/orders').subscribe(() => okCount++);
    http.get('/api/cart').subscribe(() => okCount++);

    const firstWave = httpMock.match(
      (r) =>
        ['/api/me', '/api/orders', '/api/cart'].includes(r.url) &&
        r.headers.get('Authorization') === 'Bearer old-access',
    );
    expect(firstWave.length).toBe(3);
    firstWave.forEach((r) =>
      r.flush(
        { title: 'Unauthorized', status: 401, code: 'UNAUTHORIZED' },
        { status: 401, statusText: 'Unauthorized' },
      ),
    );

    const refreshReqs = httpMock.match('/api/auth/refresh');
    expect(refreshReqs.length).toBe(1);
    expect(refreshReqs[0].request.body).toEqual({ refreshToken: 'refresh-1' });
    refreshReqs[0].flush({
      accessToken: 'new-access',
      refreshToken: 'refresh-2',
      tokenType: 'Bearer',
      expiresIn: 900,
    });

    const retries = httpMock.match(
      (r) =>
        ['/api/me', '/api/orders', '/api/cart'].includes(r.url) &&
        r.headers.get('Authorization') === 'Bearer new-access',
    );
    expect(retries.length).toBe(3);
    retries.forEach((r) => r.flush({ ok: true }));

    expect(okCount).toBe(3);
    expect(tokenStorage.getAccessToken()).toBe('new-access');
    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('logs out and navigates to login when refresh returns 401', () => {
    tokenStorage.setAccessToken('old-access');
    tokenStorage.setRefreshToken('bad-refresh', true);

    let sawError = false;
    http.get('/api/me').subscribe({
      error: () => {
        sawError = true;
      },
    });

    httpMock.expectOne('/api/me').flush(
      { title: 'Unauthorized', status: 401 },
      { status: 401, statusText: 'Unauthorized' },
    );

    httpMock.expectOne('/api/auth/refresh').flush(
      { title: 'Unauthorized', status: 401, code: 'INVALID_REFRESH_TOKEN' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(sawError).toBeTrue();
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(tokenStorage.getRefreshToken()).toBeNull();
    expect(TestBed.inject(AuthService).currentUserSnapshot).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/books' },
    });
  });

  it('logs out when 401 and no refresh token', () => {
    tokenStorage.setAccessToken('old-access');

    http.get('/api/me').subscribe({ error: () => undefined });

    httpMock.expectOne('/api/me').flush(
      { title: 'Unauthorized', status: 401 },
      { status: 401, statusText: 'Unauthorized' },
    );

    httpMock.expectNone('/api/auth/refresh');
    expect(navigateSpy).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/books' },
    });
  });
});
