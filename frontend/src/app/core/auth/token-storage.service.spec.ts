import { TestBed } from '@angular/core/testing';
import { TokenStorageService, base64UrlDecode } from './token-storage.service';

function makeJwt(payload: object): string {
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  const body = btoa(JSON.stringify(payload))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `${header}.${body}.sig`;
}

describe('TokenStorageService', () => {
  let storage: TokenStorageService;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({});
    storage = TestBed.inject(TokenStorageService);
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('keeps access token only in memory', () => {
    storage.setAccessToken('access-1');
    expect(storage.getAccessToken()).toBe('access-1');
    expect(localStorage.getItem('kitapsepeti.refreshToken')).toBeNull();
    expect(sessionStorage.getItem('kitapsepeti.refreshToken')).toBeNull();
  });

  it('stores refresh in localStorage when rememberMe is true', () => {
    storage.setRefreshToken('refresh-local', true);
    expect(localStorage.getItem('kitapsepeti.refreshToken')).toBe('refresh-local');
    expect(sessionStorage.getItem('kitapsepeti.refreshToken')).toBeNull();
    expect(storage.getRefreshToken()).toBe('refresh-local');
    expect(storage.isRememberMe()).toBeTrue();
  });

  it('stores refresh in sessionStorage when rememberMe is false', () => {
    storage.setRefreshToken('refresh-session', false);
    expect(sessionStorage.getItem('kitapsepeti.refreshToken')).toBe('refresh-session');
    expect(localStorage.getItem('kitapsepeti.refreshToken')).toBeNull();
    expect(storage.isRememberMe()).toBeFalse();
  });

  it('clear removes access and both refresh stores', () => {
    storage.setAccessToken('a');
    storage.setRefreshToken('r', true);
    storage.clear();
    expect(storage.getAccessToken()).toBeNull();
    expect(storage.getRefreshToken()).toBeNull();
  });

  it('replaceRefreshToken keeps rememberMe preference', () => {
    storage.setRefreshToken('old', true);
    storage.replaceRefreshToken('new');
    expect(localStorage.getItem('kitapsepeti.refreshToken')).toBe('new');
    expect(sessionStorage.getItem('kitapsepeti.refreshToken')).toBeNull();
  });

  it('decodeAccessToken reads sub, role and exp', () => {
    const token = makeJwt({ sub: 'user-1', role: 'USER', exp: 1700000000 });
    storage.setAccessToken(token);
    const payload = storage.decodeAccessToken();
    expect(payload?.sub).toBe('user-1');
    expect(payload?.role).toBe('USER');
    expect(payload?.exp).toBe(1700000000);
  });

  it('decodeAccessToken returns null for invalid token', () => {
    expect(storage.decodeAccessToken('not-a-jwt')).toBeNull();
  });

  it('base64UrlDecode supports unicode payload', () => {
    const encoded = btoa(unescape(encodeURIComponent('{"n":"Ali"}')))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
    expect(base64UrlDecode(encoded)).toContain('Ali');
  });
});
