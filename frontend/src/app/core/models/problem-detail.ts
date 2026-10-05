/** OpenAPI `FieldError` — `docs/api/catalog-service.openapi.json` */
export interface FieldError {
  field: string;
  message: string;
}

/** OpenAPI `Problem` (RFC 9457) — UI adıyla ProblemDetail */
export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: FieldError[];
  /** ORDER_PENDING_EXISTS / stok hatalarında taşınabilir */
  orderId?: string;
}
