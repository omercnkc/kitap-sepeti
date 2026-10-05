import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BookDetail,
  BookFilter,
  BookSummary,
  CategoryTree,
  PageResponse,
} from '../models';
import { toHttpParams } from './to-http-params';

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly base = `${environment.apiUrl}/api`;

  constructor(private readonly http: HttpClient) {}

  list(filter: BookFilter = {}): Observable<PageResponse<BookSummary>> {
    return this.http.get<PageResponse<BookSummary>>(`${this.base}/books`, {
      params: toHttpParams(filter),
    });
  }

  getById(id: string): Observable<BookDetail> {
    return this.http.get<BookDetail>(`${this.base}/books/${id}`);
  }

  categories(): Observable<CategoryTree[]> {
    return this.http.get<CategoryTree[]>(`${this.base}/categories`);
  }
}
