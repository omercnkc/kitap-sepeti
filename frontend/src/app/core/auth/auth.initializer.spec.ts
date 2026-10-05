import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { authInitializer } from './auth.initializer';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';
import { TokenResponse, UserResponse } from '../models';

describe('authInitializer', () => {
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let tokens: TokenStorageService;

  const tokenResponse: TokenResponse = {
    accessToken: 'access',
    refreshToken: 'refresh-new',
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
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    tokens = TestBed.inject(TokenStorageService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('resolves immediately when no refresh token', async () => {
    const init = authInitializer(auth, tokens);
    await expectAsync(init()).toBeResolved();
    httpMock.expectNone(() => true);
    expect(auth.currentUserSnapshot).toBeNull();
  });

  it('refreshes and loads current user when refresh exists', async () => {
    tokens.setRefreshToken('old-refresh', true);
    const init = authInitializer(auth, tokens);
    const done = init();

    const refreshReq = httpMock.expectOne('/api/auth/refresh');
    refreshReq.flush(tokenResponse);

    const meReq = httpMock.expectOne('/api/me');
    // Bearer AuthInterceptor sorumluluğunda; initializer birim testinde interceptor yok.
    meReq.flush(user);

    await expectAsync(done).toBeResolved();
    expect(auth.currentUserSnapshot).toEqual(user);
    expect(tokens.getAccessToken()).toBe('access');
  });

  it('logs out silently when refresh fails', async () => {
    tokens.setRefreshToken('bad', true);
    const init = authInitializer(auth, tokens);
    const done = init();

    httpMock.expectOne('/api/auth/refresh').flush(
      { title: 'Unauthorized', status: 401, code: 'INVALID_REFRESH_TOKEN' },
      { status: 401, statusText: 'Unauthorized' },
    );

    await expectAsync(done).toBeResolved();
    expect(auth.currentUserSnapshot).toBeNull();
    expect(tokens.getRefreshToken()).toBeNull();
  });
});
