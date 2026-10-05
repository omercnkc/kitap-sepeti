import { HTTP_INTERCEPTORS, HttpClient } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ToastService } from '../services/toast.service';
import { ErrorInterceptor } from './error.interceptor';
import { ProblemDetail } from '../models';

describe('ErrorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(() => {
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: ToastService, useValue: toast },
        { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('status 0 should toast network message and rethrow ProblemDetail', () => {
    let caught: ProblemDetail | undefined;
    http.get('/api/books').subscribe({
      next: () => fail('expected error'),
      error: (err: ProblemDetail) => {
        caught = err;
      },
    });

    httpMock.expectOne('/api/books').error(new ProgressEvent('error'));

    expect(toast.error).toHaveBeenCalledWith('Sunucuya ulaşılamıyor');
    expect(caught?.status).toBe(0);
  });

  it('403 should toast forbidden message', () => {
    http.get('/api/admin').subscribe({
      next: () => fail('expected error'),
      error: () => undefined,
    });

    httpMock.expectOne('/api/admin').flush(
      { title: 'Forbidden', status: 403, code: 'FORBIDDEN' },
      { status: 403, statusText: 'Forbidden' },
    );

    expect(toast.error).toHaveBeenCalledWith('Bu işlem için yetkiniz yok');
  });

  it('404 should not toast and should rethrow', () => {
    let caught: ProblemDetail | undefined;
    http.get('/api/books/x').subscribe({
      next: () => fail('expected error'),
      error: (err: ProblemDetail) => {
        caught = err;
      },
    });

    httpMock.expectOne('/api/books/x').flush(
      { title: 'Not Found', status: 404, code: 'NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );

    expect(toast.error).not.toHaveBeenCalled();
    expect(caught?.status).toBe(404);
  });

  it('400 without errors should toast detail', () => {
    http.post('/api/x', {}).subscribe({
      next: () => fail('expected error'),
      error: () => undefined,
    });

    httpMock.expectOne('/api/x').flush(
      { title: 'Bad Request', status: 400, detail: 'Geçersiz istek' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(toast.error).toHaveBeenCalledWith('Geçersiz istek');
  });

  it('400 with field errors should toast general validation message', () => {
    http.post('/api/x', {}).subscribe({
      next: () => fail('expected error'),
      error: () => undefined,
    });

    httpMock.expectOne('/api/x').flush(
      {
        title: 'Bad Request',
        status: 400,
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'email', message: 'must not be blank' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(toast.error).toHaveBeenCalledWith('Girdiğiniz bilgileri kontrol edin.');
  });

  it('503 should toast unavailable message', () => {
    http.get('/api/cart').subscribe({
      next: () => fail('expected error'),
      error: () => undefined,
    });

    httpMock.expectOne('/api/cart').flush(
      { title: 'Service Unavailable', status: 503, code: 'AUTHENTICATION_UNAVAILABLE' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    expect(toast.error).toHaveBeenCalledWith('Şu anda işlem yapılamıyor, tekrar deneyin');
  });

  it('409 should map known code to Turkish message', () => {
    http.post('/api/cart/items', {}).subscribe({
      next: () => fail('expected error'),
      error: () => undefined,
    });

    httpMock.expectOne('/api/cart/items').flush(
      { title: 'Conflict', status: 409, code: 'BOOK_NOT_AVAILABLE' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(toast.error).toHaveBeenCalledWith('Kitap satışa uygun değil.');
  });

  it('409 RESOURCE_IN_USE should toast in-use message', () => {
    http.delete('/api/admin/publishers/p1').subscribe({
      next: () => fail('expected error'),
      error: () => undefined,
    });

    httpMock.expectOne('/api/admin/publishers/p1').flush(
      { title: 'Conflict', status: 409, code: 'RESOURCE_IN_USE' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(toast.error).toHaveBeenCalledWith('Bu kayıt kullanımda olduğu için silinemez.');
  });

  it('409 CATEGORY_CYCLE should toast cycle message', () => {
    http.put('/api/admin/categories/c1/parent', { parentId: 'c2' }).subscribe({
      next: () => fail('expected error'),
      error: () => undefined,
    });

    httpMock.expectOne('/api/admin/categories/c1/parent').flush(
      { title: 'Conflict', status: 409, code: 'CATEGORY_CYCLE' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(toast.error).toHaveBeenCalledWith(
      'Bir kategoriyi kendi altına veya alt kategorisine taşıyamazsınız.',
    );
  });

  it('409 CONCURRENT_MODIFICATION should toast reload message', () => {
    http.patch('/api/admin/books/b1', { version: 1 }).subscribe({
      next: () => fail('expected error'),
      error: () => undefined,
    });

    httpMock.expectOne('/api/admin/books/b1').flush(
      { title: 'Conflict', status: 409, code: 'CONCURRENT_MODIFICATION' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(toast.error).toHaveBeenCalledWith(
      'Kayıt başka biri tarafından değiştirildi. Formu yeniden yükleyin ve tekrar deneyin.',
    );
  });

  it('401 should not toast (auth deferred) and rethrow', () => {
    let caught: ProblemDetail | undefined;
    http.get('/api/me').subscribe({
      next: () => fail('expected error'),
      error: (err: ProblemDetail) => {
        caught = err;
      },
    });

    httpMock.expectOne('/api/me').flush(
      { title: 'Unauthorized', status: 401, code: 'UNAUTHORIZED' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(toast.error).not.toHaveBeenCalled();
    expect(caught?.status).toBe(401);
  });
});
