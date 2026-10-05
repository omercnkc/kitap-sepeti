import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TokenStorageService } from '../auth/token-storage.service';
import {
  LoginRequest,
  RefreshRequest,
  RegisterRequest,
  TokenResponse,
  UserResponse,
} from '../models';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly base = `${environment.apiUrl}/api`;

  constructor(
    private readonly http: HttpClient,
    private readonly tokenStorage: TokenStorageService,
  ) {}

  register(body: RegisterRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.base}/auth/register`, body);
  }

  login(body: LoginRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.base}/auth/login`, body);
  }

  refresh(body: RefreshRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.base}/auth/refresh`, body);
  }

  /**
   * AuthInterceptor gelene kadar access token burada eklenir.
   */
  getMe(): Observable<UserResponse> {
    const token = this.tokenStorage.getAccessToken();
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    return this.http.get<UserResponse>(`${this.base}/me`, { headers });
  }
}
