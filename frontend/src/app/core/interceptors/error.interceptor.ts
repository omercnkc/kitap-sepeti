import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { FieldError, ProblemDetail } from '../models';
import { ToastService } from '../services/toast.service';
import { messageForErrorCode } from './error-messages';

@Injectable()
export class ErrorInterceptor implements HttpInterceptor {
  constructor(private readonly toastService: ToastService) {}

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    return next.handle(req).pipe(
      catchError((err: unknown) => {
        const problem = toProblemDetail(err);
        this.notify(problem);
        return throwError(() => problem);
      }),
    );
  }

  private notify(problem: ProblemDetail): void {
    const status = problem.status;

    if (status === 0) {
      this.toastService.error('Sunucuya ulaşılamıyor');
      return;
    }

    if (status === 400) {
      if (problem.errors && problem.errors.length > 0) {
        this.toastService.error('Girdiğiniz bilgileri kontrol edin.');
      } else {
        this.toastService.error(this.resolveMessage(problem));
      }
      return;
    }

    if (status === 401) {
      // AuthInterceptor refresh/logout yönetir; burada toast yok.
      return;
    }

    if (status === 403) {
      this.toastService.error('Bu işlem için yetkiniz yok');
      return;
    }

    if (status === 404) {
      // Detay sayfaları EmptyState ile yönetir.
      return;
    }

    if (status === 409) {
      this.toastService.error(this.resolveMessage(problem));
      return;
    }

    if (status === 503) {
      this.toastService.error('Şu anda işlem yapılamıyor, tekrar deneyin');
      return;
    }

    if (status >= 500) {
      this.toastService.error('Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.');
      return;
    }

    this.toastService.error(this.resolveMessage(problem));
  }

  private resolveMessage(problem: ProblemDetail): string {
    const byCode = messageForErrorCode(problem.code);
    if (byCode) {
      return byCode;
    }
    if (problem.detail && problem.detail.trim().length > 0) {
      return problem.detail;
    }
    if (problem.title && problem.title.trim().length > 0) {
      return problem.title;
    }
    return 'Bir hata oluştu.';
  }
}

export function toProblemDetail(err: unknown): ProblemDetail {
  if (isProblemDetail(err)) {
    return err;
  }

  if (err instanceof HttpErrorResponse) {
    const body = err.error;
    if (isProblemDetail(body)) {
      return {
        ...body,
        status: typeof body.status === 'number' ? body.status : err.status,
      };
    }

    if (err.status === 0) {
      return {
        title: 'Network Error',
        status: 0,
        detail: 'Sunucuya ulaşılamıyor',
        code: undefined,
      };
    }

    return {
      title: err.statusText || 'Error',
      status: err.status,
      detail: typeof body === 'string' && body.length > 0 ? body : undefined,
    };
  }

  return {
    title: 'Error',
    status: 500,
    detail: 'Beklenmeyen bir hata oluştu.',
  };
}

function isProblemDetail(value: unknown): value is ProblemDetail {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const candidate = value as Partial<ProblemDetail>;
  return typeof candidate.status === 'number' && typeof candidate.title === 'string';
}

export function fieldErrorsOf(problem: ProblemDetail): FieldError[] {
  return problem.errors ?? [];
}
