import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
import { AuthApi } from '../api/auth.api';
import {
  LoginRequest,
  RegisterRequest,
  TokenResponse,
  UserResponse,
} from '../models';
import { TokenStorageService } from './token-storage.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly currentUserSubject = new BehaviorSubject<UserResponse | null>(null);

  readonly currentUser$: Observable<UserResponse | null> =
    this.currentUserSubject.asObservable();

  constructor(
    private readonly authApi: AuthApi,
    private readonly tokenStorage: TokenStorageService,
  ) {}

  get currentUserSnapshot(): UserResponse | null {
    return this.currentUserSubject.value;
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

  logout(): void {
    this.tokenStorage.clear();
    this.currentUserSubject.next(null);
  }

  private persistTokens(tokens: TokenResponse, rememberMe: boolean): void {
    this.tokenStorage.setAccessToken(tokens.accessToken);
    this.tokenStorage.setRefreshToken(tokens.refreshToken, rememberMe);
  }
}
