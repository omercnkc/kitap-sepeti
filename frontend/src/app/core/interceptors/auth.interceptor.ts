import {
  HttpContextToken,
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
} from '@angular/common/http';
import { Injectable, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, map, shareReplay, switchMap } from 'rxjs/operators';
import { AuthService } from '../auth/auth.service';
import { TokenStorageService } from '../auth/token-storage.service';
import { ProblemDetail } from '../models';

/** Bu istek 401 sonrası bir kez yenilendi; ikinci 401'de logout. */
export const AUTH_RETRIED = new HttpContextToken<boolean>(() => false);

/**
 * Access token ekler; 401'de tek uçuşlu refresh + bir kez retry.
 * Public auth uçlarına Authorization eklemez / refresh denemez.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private refreshShared$: Observable<string> | null = null;

  constructor(
    private readonly injector: Injector,
    private readonly tokenStorage: TokenStorageService,
    private readonly router: Router,
  ) {}

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const outgoing = this.isPublicAuthUrl(req.url)
      ? req
      : this.attachAccessToken(req);

    return next.handle(outgoing).pipe(
      catchError((err: unknown) => this.handleError(err, outgoing, next)),
    );
  }

  private handleError(
    err: unknown,
    req: HttpRequest<unknown>,
    next: HttpHandler,
  ): Observable<HttpEvent<unknown>> {
    if (this.statusOf(err) !== 401 || this.isPublicAuthUrl(req.url)) {
      return throwError(() => err);
    }

    if (req.context.get(AUTH_RETRIED)) {
      this.redirectToLogin();
      return throwError(() => err);
    }

    if (!this.tokenStorage.getRefreshToken()) {
      this.redirectToLogin();
      return throwError(() => err);
    }

    return this.refreshAccessTokenOnce().pipe(
      switchMap((accessToken) => {
        const retryReq = req.clone({
          setHeaders: { Authorization: `Bearer ${accessToken}` },
          context: req.context.set(AUTH_RETRIED, true),
        });
        return next.handle(retryReq);
      }),
      catchError((refreshErr: unknown) => throwError(() => refreshErr)),
    );
  }

  private refreshAccessTokenOnce(): Observable<string> {
    if (!this.refreshShared$) {
      this.refreshShared$ = this.injector.get(AuthService).refresh().pipe(
        map((tokens) => tokens.accessToken),
        catchError((err: unknown) => {
          this.redirectToLogin();
          return throwError(() => err);
        }),
        finalize(() => {
          this.refreshShared$ = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshShared$;
  }

  private attachAccessToken(req: HttpRequest<unknown>): HttpRequest<unknown> {
    const token = this.tokenStorage.getAccessToken();
    if (!token) {
      return req;
    }
    return req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }

  private isPublicAuthUrl(url: string): boolean {
    return (
      url.includes('/api/auth/login') ||
      url.includes('/api/auth/register') ||
      url.includes('/api/auth/refresh')
    );
  }

  private statusOf(err: unknown): number {
    if (err instanceof HttpErrorResponse) {
      return err.status;
    }
    if (err && typeof err === 'object' && typeof (err as ProblemDetail).status === 'number') {
      return (err as ProblemDetail).status;
    }
    return 0;
  }

  private redirectToLogin(): void {
    this.injector.get(AuthService).logout();
    const returnUrl = this.safeReturnUrl(this.router.url);
    void this.router.navigate(['/login'], {
      queryParams: returnUrl ? { returnUrl } : undefined,
    });
  }

  private safeReturnUrl(url: string): string | null {
    if (!url || url === '/' || url.startsWith('/login') || url.startsWith('/register')) {
      return null;
    }
    if (url.startsWith('/') && !url.startsWith('//')) {
      return url;
    }
    return null;
  }
}
