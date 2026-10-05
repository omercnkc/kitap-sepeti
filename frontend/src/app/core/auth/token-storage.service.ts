import { Injectable } from '@angular/core';
import { JwtPayload } from '../models';

const REFRESH_KEY = 'kitapsepeti.refreshToken';

/**
 * Access token yalnızca bellekte; refresh rememberMe'ye göre local/session storage.
 * JWT imzası istemcide doğrulanmaz — yalnızca payload okunur.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  private accessToken: string | null = null;

  getAccessToken(): string | null {
    return this.accessToken;
  }

  setAccessToken(token: string | null): void {
    this.accessToken = token;
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY) ?? sessionStorage.getItem(REFRESH_KEY);
  }

  /**
   * Refresh token'ı kalıcı (local) veya oturum (session) deposuna yazar.
   * Diğer depoyu temizler ki tek kaynak kalsın.
   */
  setRefreshToken(token: string, rememberMe: boolean): void {
    this.clearRefreshStores();
    if (rememberMe) {
      localStorage.setItem(REFRESH_KEY, token);
    } else {
      sessionStorage.setItem(REFRESH_KEY, token);
    }
  }

  /** Mevcut refresh hangi depodaysa oraya yeni değeri yazar (refresh rotation). */
  replaceRefreshToken(token: string): void {
    const rememberMe = this.isRememberMe();
    this.setRefreshToken(token, rememberMe);
  }

  isRememberMe(): boolean {
    return localStorage.getItem(REFRESH_KEY) !== null;
  }

  clear(): void {
    this.accessToken = null;
    this.clearRefreshStores();
  }

  /**
   * JWT payload'ını base64url ile çözer. Geçersiz token'da null.
   */
  decodeAccessToken(token: string | null = this.accessToken): JwtPayload | null {
    if (!token) {
      return null;
    }
    const parts = token.split('.');
    if (parts.length < 2) {
      return null;
    }
    try {
      const json = base64UrlDecode(parts[1]);
      return JSON.parse(json) as JwtPayload;
    } catch {
      return null;
    }
  }

  private clearRefreshStores(): void {
    localStorage.removeItem(REFRESH_KEY);
    sessionStorage.removeItem(REFRESH_KEY);
  }
}

export function base64UrlDecode(input: string): string {
  let base64 = input.replace(/-/g, '+').replace(/_/g, '/');
  const pad = base64.length % 4;
  if (pad === 2) {
    base64 += '==';
  } else if (pad === 3) {
    base64 += '=';
  } else if (pad === 1) {
    base64 += '===';
  }
  return decodeURIComponent(
    atob(base64)
      .split('')
      .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
      .join(''),
  );
}
