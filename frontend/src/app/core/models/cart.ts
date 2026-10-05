/** OpenAPI `CatalogStatus` */
export type CatalogStatus = 'VERIFIED' | 'UNAVAILABLE';

/** OpenAPI `AddCartItemRequest` */
export interface AddCartItemRequest {
  bookId: string;
  quantity?: number;
}

/** OpenAPI `UpdateCartItemRequest` */
export interface UpdateCartItemRequest {
  quantity: number;
}

/** OpenAPI `CartLineResponse` */
export interface CartLineResponse {
  bookId: string;
  title: string;
  coverUrl: string | null;
  quantity: number;
  currency: string;
  snapshotUnitPrice: number;
  currentUnitPrice: number | null;
  lineTotal: number;
  priceChanged: boolean;
  available: boolean | null;
}

/** OpenAPI `CartResponse` */
export interface CartResponse {
  catalogStatus: CatalogStatus;
  currency: string | null;
  itemCount: number;
  lineCount: number;
  subtotal: number | null;
  items: CartLineResponse[];
}
