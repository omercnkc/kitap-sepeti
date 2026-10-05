import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
import { AccountApi } from '../api/account.api';
import { AuthApi } from '../api/auth.api';
import {
  LoginRequest,
  RegisterRequest,
  TokenResponse,
  UpdateProfileRequest,
  UserResponse,
} from '../models';
import { REFRESH_TOKEN_STORAGE_KEY, TokenStorageService } from './token-storage.service';

@Injectable({ providedIn: 'root' })
export class AuthService implements OnDestroy {
  private readonly currentUserSubject = new BehaviorSubject<UserResponse | null>(null);
  private readonly onStorage = (event: StorageEvent): void => this.handleStorageEvent(event);

  readonly currentUser$: Observable<UserResponse | null> =
    this.currentUserSubject.asObservable();

  constructor(
    private readonly authApi: AuthApi,
    private readonly accountApi: AccountApi,
    private readonly tokenStorage: TokenStorageService,
  ) {
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', this.onStorage);
    }
  }

  ngOnDestroy(): void {
    if (typeof window !== 'undefined') {
      window.removeEventListener('storage', this.onStorage);
    }
  }

  get currentUserSnapshot(): UserResponse | null {
    return this.currentUserSubject.value;
  }

  isLoggedIn(): boolean {
    return this.currentUserSubject.value !== null;
  }

  isAdmin(user: UserResponse | null = this.currentUserSubject.value): boolean {
    if (user?.role === 'ADMIN') {
      return true;
    }
    const role = this.tokenStorage.decodeAccessToken()?.role;
    return role === 'ADMIN' || role === 'ROLE_ADMIN';
  }

  login(email: string, password: string, rememberMe: boolean): Observable<UserResponse> {
    const body: LoginRequest = { email, password };
    return this.authApi.login(body).pipe(
      tap((tokens) => this.persistTokens(tokens, rememberMe)),
      switchMap(() => this.loadCurrentUser()),
    );
  }

  register(request: RegisterRequest, rememberMe = false): Observable<UserResponse> {
    return this.authApi.register(request).pipe(
      tap((tokens) => this.persistTokens(tokens, rememberMe)),
      switchMap(() => this.loadCurrentUser()),
    );
  }

  refresh(): Observable<TokenResponse> {
    const refreshToken = this.tokenStorage.getRefreshToken();
    if (!refreshToken) {
      this.logout();
      return throwError(() => new Error('Refresh token yok'));
    }
    return this.authApi.refresh({ refreshToken }).pipe(
      tap((tokens) => {
        this.tokenStorage.setAccessToken(tokens.accessToken);
        this.tokenStorage.replaceRefreshToken(tokens.refreshToken);
      }),
    );
  }

  loadCurrentUser(): Observable<UserResponse> {
    return this.authApi.getMe().pipe(
      tap((user) => this.currentUserSubject.next(user)),
    );
  }

  getMe(): Observable<UserResponse> {
    return this.loadCurrentUser();
  }

  updateProfile(body: UpdateProfileRequest): Observable<UserResponse> {
    return this.accountApi.updateProfile(body).pipe(
      tap((user) => this.currentUserSubject.next(user)),
    );
  }

  logout(): void {
    this.tokenStorage.clear();
    this.currentUserSubject.next(null);
  }

  private persistTokens(tokens: TokenResponse, rememberMe: boolean): void {
    this.tokenStorage.setAccessToken(tokens.accessToken);
    this.tokenStorage.setRefreshToken(tokens.refreshToken, rememberMe);
  }

  /**
   * Başka sekmede localStorage refresh silinince bu sekmedeki oturumu düşür.
   * (sessionStorage sekmeler arası paylaşılmaz — tarayıcı davranışı.)
   */
  private handleStorageEvent(event: StorageEvent): void {
    if (event.key !== null && event.key !== REFRESH_TOKEN_STORAGE_KEY) {
      return;
    }
    // key === null → clear(); veya refresh silindi
    if (event.key === REFRESH_TOKEN_STORAGE_KEY && event.newValue !== null) {
      return;
    }
    this.tokenStorage.setAccessToken(null);
    this.currentUserSubject.next(null);
  }
}
