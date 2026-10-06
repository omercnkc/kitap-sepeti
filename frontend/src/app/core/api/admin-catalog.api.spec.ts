import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  AdminBook,
  AdminBookSummary,
  AuthorResponse,
  CategoryResponse,
  PageResponse,
  PublisherResponse,
} from '../models';
import { AdminCatalogApi } from './admin-catalog.api';

describe('AdminCatalogApi', () => {
  let api: AdminCatalogApi;
  let httpMock: HttpTestingController;

  const publisher: PublisherResponse = {
    id: 'p1',
    name: 'Can',
    slug: 'can',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };

  const author: AuthorResponse = {
    id: 'a1',
    name: 'Orhan Pamuk',
    slug: 'orhan-pamuk',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };

  const category: CategoryResponse = {
    id: 'c1',
    name: 'Roman',
    slug: 'roman',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };

  const bookSummary: AdminBookSummary = {
    id: 'b1',
    title: 'Kar',
    publisher: { id: 'p1', name: 'Can', slug: 'can' },
    priceAmount: 120,
    currency: 'TRY',
    status: 'draft',
    stockQuantity: 10,
    reservedQuantity: 0,
    availableQuantity: 10,
    updatedAt: '2026-01-01T00:00:00Z',
    version: 1,
  };

  const book: AdminBook = {
    ...bookSummary,
    authors: [{ id: 'a1', name: 'Orhan Pamuk', slug: 'orhan-pamuk' }],
    categories: [{ id: 'c1', name: 'Roman', slug: 'roman' }],
    createdAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    api = TestBed.inject(AdminCatalogApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listPublishers should GET /api/admin/publishers', () => {
    const page: PageResponse<PublisherResponse> = {
      items: [publisher],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    api.listPublishers({ page: 0, size: 20 }).subscribe((res) => expect(res).toEqual(page));
    const req = httpMock.expectOne('/api/admin/publishers?page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush(page);
  });

  it('createPublisher should POST /api/admin/publishers', () => {
    api.createPublisher({ name: 'Can' }).subscribe((res) => expect(res).toEqual(publisher));
    const req = httpMock.expectOne('/api/admin/publishers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Can' });
    req.flush(publisher, { status: 201, statusText: 'Created' });
  });

  it('updatePublisher should PATCH /api/admin/publishers/:id', () => {
    api.updatePublisher('p1', { name: 'Can Yayınları' }).subscribe((res) => expect(res.id).toBe('p1'));
    const req = httpMock.expectOne('/api/admin/publishers/p1');
    expect(req.request.method).toBe('PATCH');
    req.flush({ ...publisher, name: 'Can Yayınları' });
  });

  it('listAuthors should GET /api/admin/authors', () => {
    const page: PageResponse<AuthorResponse> = {
      items: [author],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    api.listAuthors().subscribe((res) => expect(res.items[0].slug).toBe('orhan-pamuk'));
    const req = httpMock.expectOne('/api/admin/authors');
    expect(req.request.method).toBe('GET');
    req.flush(page);
  });

  it('createAuthor should POST /api/admin/authors', () => {
    api.createAuthor({ name: 'Orhan Pamuk', slug: 'orhan-pamuk' }).subscribe((res) => {
      expect(res).toEqual(author);
    });
    const req = httpMock.expectOne('/api/admin/authors');
    expect(req.request.method).toBe('POST');
    req.flush(author, { status: 201, statusText: 'Created' });
  });

  it('listCategories should GET /api/admin/categories', () => {
    const page: PageResponse<CategoryResponse> = {
      items: [category],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    api.listCategories({ page: 1 }).subscribe((res) => expect(res.items.length).toBe(1));
    const req = httpMock.expectOne('/api/admin/categories?page=1');
    expect(req.request.method).toBe('GET');
    req.flush(page);
  });

  it('updateCategory should PATCH /api/admin/categories/:id', () => {
    api.updateCategory('c1', { name: 'Romanlar' }).subscribe((res) => expect(res.id).toBe('c1'));
    const req = httpMock.expectOne('/api/admin/categories/c1');
    expect(req.request.method).toBe('PATCH');
    req.flush({ ...category, name: 'Romanlar' });
  });

  it('listBooks should GET /api/admin/books with status', () => {
    const page: PageResponse<AdminBookSummary> = {
      items: [bookSummary],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    api.listBooks({ status: 'draft', page: 0 }).subscribe((res) => {
      expect(res.items[0].status).toBe('draft');
    });
    const req = httpMock.expectOne('/api/admin/books?status=draft&page=0');
    expect(req.request.method).toBe('GET');
    req.flush(page);
  });

  it('createBook should POST /api/admin/books', () => {
    api
      .createBook({ title: 'Kar', publisherName: 'YKY', priceAmount: 120 })
      .subscribe((res) => expect(res).toEqual(book));
    const req = httpMock.expectOne('/api/admin/books');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.publisherName).toBe('YKY');
    req.flush(book, { status: 201, statusText: 'Created' });
  });

  it('updateBook should PATCH /api/admin/books/:id', () => {
    api.updateBook('b1', { version: 1, title: 'Kar (yeni)' }).subscribe((res) => {
      expect(res.id).toBe('b1');
    });
    const req = httpMock.expectOne('/api/admin/books/b1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body.version).toBe(1);
    req.flush(book);
  });

  it('publishBook should POST /api/admin/books/:id/publish', () => {
    api.publishBook('b1').subscribe((res) => expect(res.id).toBe('b1'));
    const req = httpMock.expectOne('/api/admin/books/b1/publish');
    expect(req.request.method).toBe('POST');
    req.flush({ ...book, status: 'published' });
  });

  it('archiveBook should POST /api/admin/books/:id/archive', () => {
    api.archiveBook('b1').subscribe((res) => expect(res.status).toBe('archived'));
    const req = httpMock.expectOne('/api/admin/books/b1/archive');
    expect(req.request.method).toBe('POST');
    req.flush({ ...book, status: 'archived' });
  });

  it('adjustBookStock should POST /api/admin/books/:id/stock-adjustments', () => {
    api.adjustBookStock('b1', { delta: -2 }).subscribe((res) => {
      expect(res.stockQuantity).toBe(8);
    });
    const req = httpMock.expectOne('/api/admin/books/b1/stock-adjustments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ delta: -2 });
    req.flush({ ...book, stockQuantity: 8, availableQuantity: 8 });
  });
});
