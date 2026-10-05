import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AddressResponse,
  CreateAddressRequest,
  UpdateAddressRequest,
  UpdateProfileRequest,
  UserResponse,
} from '../models';

@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly base = `${environment.apiUrl}/api/me`;

  constructor(private readonly http: HttpClient) {}

  updateProfile(body: UpdateProfileRequest): Observable<UserResponse> {
    return this.http.patch<UserResponse>(this.base, body);
  }

  listAddresses(): Observable<AddressResponse[]> {
    return this.http.get<AddressResponse[]>(`${this.base}/addresses`);
  }

  getAddress(id: string): Observable<AddressResponse> {
    return this.http.get<AddressResponse>(`${this.base}/addresses/${id}`);
  }

  createAddress(body: CreateAddressRequest): Observable<AddressResponse> {
    return this.http.post<AddressResponse>(`${this.base}/addresses`, body);
  }

  updateAddress(id: string, body: UpdateAddressRequest): Observable<AddressResponse> {
    return this.http.patch<AddressResponse>(`${this.base}/addresses/${id}`, body);
  }

  deleteAddress(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/addresses/${id}`);
  }

  /** OpenAPI: ayrı uç yok; `isDefault: true` ile PATCH. */
  setDefaultAddress(id: string): Observable<AddressResponse> {
    return this.updateAddress(id, { isDefault: true });
  }
}
