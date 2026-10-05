import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AddCartItemRequest,
  CartResponse,
  UpdateCartItemRequest,
} from '../models';

@Injectable({ providedIn: 'root' })
export class CartApi {
  private readonly base = `${environment.apiUrl}/api/cart`;

  constructor(private readonly http: HttpClient) {}

  getCart(): Observable<CartResponse> {
    return this.http.get<CartResponse>(this.base);
  }

  addItem(body: AddCartItemRequest): Observable<CartResponse> {
    return this.http.post<CartResponse>(`${this.base}/items`, body);
  }

  updateItem(bookId: string, body: UpdateCartItemRequest): Observable<CartResponse> {
    return this.http.patch<CartResponse>(`${this.base}/items/${bookId}`, body);
  }

  removeItem(bookId: string): Observable<CartResponse> {
    return this.http.delete<CartResponse>(`${this.base}/items/${bookId}`);
  }

  clear(): Observable<CartResponse> {
    return this.http.delete<CartResponse>(`${this.base}/items`);
  }
}
