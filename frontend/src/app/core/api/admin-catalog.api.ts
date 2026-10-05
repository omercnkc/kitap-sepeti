import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AdminBook,
  AdminBookQuery,
  AdminBookSummary,
  AdminPageQuery,
  AuthorResponse,
  CategoryResponse,
  CreateAuthorRequest,
  CreateBookRequest,
  CreateCategoryRequest,
  CreatePublisherRequest,
  MoveCategoryRequest,
  PageResponse,
  PublisherResponse,
  StockAdjustmentRequest,
  UpdateAuthorRequest,
  UpdateBookRequest,
  UpdateCategoryRequest,
  UpdatePublisherRequest,
} from '../models';
import { toHttpParams } from './to-http-params';

@Injectable({ providedIn: 'root' })
export class AdminCatalogApi {
  private readonly base = `${environment.apiUrl}/api/admin`;

  constructor(private readonly http: HttpClient) {}

  // —— Publishers ——

  listPublishers(query: AdminPageQuery = {}): Observable<PageResponse<PublisherResponse>> {
    return this.http.get<PageResponse<PublisherResponse>>(`${this.base}/publishers`, {
      params: toHttpParams(query),
    });
  }

  getPublisher(id: string): Observable<PublisherResponse> {
    return this.http.get<PublisherResponse>(`${this.base}/publishers/${id}`);
  }

  createPublisher(body: CreatePublisherRequest): Observable<PublisherResponse> {
    return this.http.post<PublisherResponse>(`${this.base}/publishers`, body);
  }

  updatePublisher(id: string, body: UpdatePublisherRequest): Observable<PublisherResponse> {
    return this.http.patch<PublisherResponse>(`${this.base}/publishers/${id}`, body);
  }

  deletePublisher(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/publishers/${id}`);
  }

  // —— Authors ——

  listAuthors(query: AdminPageQuery = {}): Observable<PageResponse<AuthorResponse>> {
    return this.http.get<PageResponse<AuthorResponse>>(`${this.base}/authors`, {
      params: toHttpParams(query),
    });
  }

  getAuthor(id: string): Observable<AuthorResponse> {
    return this.http.get<AuthorResponse>(`${this.base}/authors/${id}`);
  }

  createAuthor(body: CreateAuthorRequest): Observable<AuthorResponse> {
    return this.http.post<AuthorResponse>(`${this.base}/authors`, body);
  }

  updateAuthor(id: string, body: UpdateAuthorRequest): Observable<AuthorResponse> {
    return this.http.patch<AuthorResponse>(`${this.base}/authors/${id}`, body);
  }

  deleteAuthor(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/authors/${id}`);
  }

  // —— Categories ——

  listCategories(query: AdminPageQuery = {}): Observable<PageResponse<CategoryResponse>> {
    return this.http.get<PageResponse<CategoryResponse>>(`${this.base}/categories`, {
      params: toHttpParams(query),
    });
  }

  getCategory(id: string): Observable<CategoryResponse> {
    return this.http.get<CategoryResponse>(`${this.base}/categories/${id}`);
  }

  createCategory(body: CreateCategoryRequest): Observable<CategoryResponse> {
    return this.http.post<CategoryResponse>(`${this.base}/categories`, body);
  }

  updateCategory(id: string, body: UpdateCategoryRequest): Observable<CategoryResponse> {
    return this.http.patch<CategoryResponse>(`${this.base}/categories/${id}`, body);
  }

  moveCategory(id: string, body: MoveCategoryRequest): Observable<CategoryResponse> {
    return this.http.put<CategoryResponse>(`${this.base}/categories/${id}/parent`, body);
  }

  deleteCategory(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/categories/${id}`);
  }

  // —— Books ——

  listBooks(query: AdminBookQuery = {}): Observable<PageResponse<AdminBookSummary>> {
    return this.http.get<PageResponse<AdminBookSummary>>(`${this.base}/books`, {
      params: toHttpParams(query),
    });
  }

  getBook(id: string): Observable<AdminBook> {
    return this.http.get<AdminBook>(`${this.base}/books/${id}`);
  }

  createBook(body: CreateBookRequest): Observable<AdminBook> {
    return this.http.post<AdminBook>(`${this.base}/books`, body);
  }

  updateBook(id: string, body: UpdateBookRequest): Observable<AdminBook> {
    return this.http.patch<AdminBook>(`${this.base}/books/${id}`, body);
  }

  deleteBook(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/books/${id}`);
  }

  publishBook(id: string): Observable<AdminBook> {
    return this.http.post<AdminBook>(`${this.base}/books/${id}/publish`, {});
  }

  archiveBook(id: string): Observable<AdminBook> {
    return this.http.post<AdminBook>(`${this.base}/books/${id}/archive`, {});
  }

  adjustBookStock(id: string, body: StockAdjustmentRequest): Observable<AdminBook> {
    return this.http.post<AdminBook>(`${this.base}/books/${id}/stock-adjustments`, body);
  }
}
