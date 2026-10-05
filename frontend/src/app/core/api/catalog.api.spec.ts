import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogApi } from './catalog.api';
import { BookDetail, BookSummary, CategoryTree, PageResponse } from '../models';

describe('CatalogApi', () => {
  let api: CatalogApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    api = TestBed.inject(CatalogApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list should GET /api/books with filtered query params', () => {
    const body: PageResponse<BookSummary> = {
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    };

    api.list({ categoryId: 'cat-1', page: 0, size: 20, sort: '', authorId: null }).subscribe((res) => {
      expect(res).toEqual(body);
    });

    const req = httpMock.expectOne((r) => r.url === '/api/books');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('categoryId')).toBe('cat-1');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('sort')).toBeFalse();
    expect(req.request.params.has('authorId')).toBeFalse();
    req.flush(body);
  });

  it('getById should GET /api/books/:id', () => {
    const body = { id: 'book-1', title: 'Deneme' } as BookDetail;

    api.getById('book-1').subscribe((res) => {
      expect(res.id).toBe('book-1');
    });

    const req = httpMock.expectOne('/api/books/book-1');
    expect(req.request.method).toBe('GET');
    req.flush(body);
  });

  it('categories should GET /api/categories', () => {
    const body: CategoryTree[] = [];

    api.categories().subscribe((res) => {
      expect(res).toEqual(body);
    });

    const req = httpMock.expectOne('/api/categories');
    expect(req.request.method).toBe('GET');
    req.flush(body);
  });
});
