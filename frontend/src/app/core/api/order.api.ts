import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CheckoutRequest,
  OrderListParams,
  OrderResponse,
  OrderSummaryResponse,
  PageResponse,
} from '../models';

@Injectable({ providedIn: 'root' })
export class OrderApi {
  private readonly base = `${environment.apiUrl}/api/orders`;

  constructor(private readonly http: HttpClient) {}

  checkout(body: CheckoutRequest): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(`${this.base}/checkout`, body);
  }

  getById(id: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.base}/${id}`);
  }

  list(params: OrderListParams = {}): Observable<PageResponse<OrderSummaryResponse>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined && params.page !== null) {
      httpParams = httpParams.set('page', String(params.page));
    }
    if (params.size !== undefined && params.size !== null) {
      httpParams = httpParams.set('size', String(params.size));
    }
    return this.http.get<PageResponse<OrderSummaryResponse>>(this.base, {
      params: httpParams,
    });
  }
}
