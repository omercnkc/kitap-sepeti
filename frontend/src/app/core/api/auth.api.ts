import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
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

  constructor(private readonly http: HttpClient) {}

  register(body: RegisterRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.base}/auth/register`, body);
  }

  login(body: LoginRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.base}/auth/login`, body);
  }

  refresh(body: RefreshRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.base}/auth/refresh`, body);
  }

  getMe(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.base}/me`);
  }
}
