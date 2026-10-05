/** OpenAPI `UpdateProfileRequest` */
export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
}

/** OpenAPI `CreateAddressRequest` */
export interface CreateAddressRequest {
  recipientName: string;
  phone: string;
  line1: string;
  city: string;
  label?: string;
  line2?: string;
  district?: string;
  postalCode?: string;
  country?: string;
  isDefault?: boolean;
}

/** OpenAPI `UpdateAddressRequest` */
export interface UpdateAddressRequest {
  recipientName?: string;
  phone?: string;
  line1?: string;
  line2?: string;
  district?: string;
  city?: string;
  postalCode?: string;
  country?: string;
  label?: string;
  isDefault?: boolean;
}

/** OpenAPI `AddressResponse` */
export interface AddressResponse {
  id: string;
  label: string | null;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  district: string | null;
  city: string;
  postalCode: string | null;
  country: string;
  isDefault: boolean;
  createdAt: string;
}
