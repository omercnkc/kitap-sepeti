/** OpenAPI `AddressRequest` (checkout gövdesi) */
export interface AddressRequest {
  recipientName: string;
  phone: string;
  line1: string;
  city: string;
  country: string;
  line2?: string;
  district?: string;
  postalCode?: string;
}

/** OpenAPI `CheckoutRequest` */
export interface CheckoutRequest {
  address: AddressRequest;
}

/** OpenAPI `OrderResponse.status` / `OrderSummaryResponse.status` */
export type OrderStatus = 'pending' | 'paid' | 'failed';

/** OpenAPI `OrderAddressResponse` */
export interface OrderAddressResponse {
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  district: string | null;
  city: string;
  postalCode: string | null;
  country: string;
}

/** OpenAPI `OrderItemResponse` */
export interface OrderItemResponse {
  bookId: string;
  title: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

/** OpenAPI `OrderResponse` */
export interface OrderResponse {
  id: string;
  status: OrderStatus;
  currency: string;
  subtotal: number;
  discountAmount: number;
  totalAmount: number;
  failureCode: string | null;
  createdAt: string;
  updatedAt: string;
  address: OrderAddressResponse;
  items: OrderItemResponse[];
}

/** OpenAPI `OrderSummaryResponse` */
export interface OrderSummaryResponse {
  id: string;
  status: OrderStatus;
  currency: string;
  totalAmount: number;
  itemCount: number;
  failureCode: string | null;
  createdAt: string;
  updatedAt: string;
}

/** OpenAPI `GET /api/orders` sorgu parametreleri */
export interface OrderListParams {
  page?: number;
  size?: number;
}
