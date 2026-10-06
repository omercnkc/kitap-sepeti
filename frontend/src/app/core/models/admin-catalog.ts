import { AuthorRef, CategoryRef, PublisherRef } from './catalog';

/** OpenAPI `AdminBookResponse.status` / list filter */
export type AdminBookStatus = 'draft' | 'published' | 'archived';

/** OpenAPI `PublisherResponse` */
export interface PublisherResponse {
  id: string;
  name: string;
  slug: string;
  createdAt: string;
  updatedAt: string;
}

/** OpenAPI `CreatePublisherRequest` */
export interface CreatePublisherRequest {
  name: string;
  slug?: string;
}

/** OpenAPI `UpdatePublisherRequest` */
export interface UpdatePublisherRequest {
  name?: string;
  slug?: string;
}

/** OpenAPI `AuthorResponse` */
export interface AuthorResponse {
  id: string;
  name: string;
  slug: string;
  createdAt: string;
  updatedAt: string;
}

/** OpenAPI `CreateAuthorRequest` */
export interface CreateAuthorRequest {
  name: string;
  slug?: string;
}

/** OpenAPI `UpdateAuthorRequest` */
export interface UpdateAuthorRequest {
  name?: string;
  slug?: string;
}

/** OpenAPI `CategoryResponse` */
export interface CategoryResponse {
  id: string;
  name: string;
  slug: string;
  parentId?: string;
  createdAt: string;
  updatedAt: string;
}

/** OpenAPI `CreateCategoryRequest` */
export interface CreateCategoryRequest {
  name: string;
  parentId?: string;
  slug?: string;
}

/** OpenAPI `UpdateCategoryRequest` */
export interface UpdateCategoryRequest {
  name?: string;
  slug?: string;
}

/** OpenAPI `MoveCategoryRequest` */
export interface MoveCategoryRequest {
  parentId?: string | null;
}

/** OpenAPI `AdminBookSummaryResponse` */
export interface AdminBookSummary {
  id: string;
  title: string;
  publisher: PublisherRef;
  priceAmount: number;
  currency: string;
  status: AdminBookStatus;
  stockQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  updatedAt: string;
  version: number;
}

/** OpenAPI `AdminBookResponse` */
export interface AdminBook {
  id: string;
  title: string;
  authors: AuthorRef[];
  publisher: PublisherRef;
  categories: CategoryRef[];
  priceAmount: number;
  currency: string;
  status: AdminBookStatus;
  stockQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  coverUrl?: string;
  description?: string;
  isbn?: string;
  pageCount?: number;
  publishedAt?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

/** OpenAPI `CreateBookRequest` */
export interface CreateBookRequest {
  title: string;
  publisherName: string;
  priceAmount: number;
  authorNames?: string[];
  categoryIds?: string[];
  coverUrl?: string;
  description?: string;
  initialStock?: number;
  isbn?: string;
  pageCount?: number;
}

/** OpenAPI `UpdateBookRequest` */
export interface UpdateBookRequest {
  version: number;
  title?: string;
  publisherName?: string;
  priceAmount?: number;
  authorNames?: string[];
  categoryIds?: string[];
  coverUrl?: string;
  description?: string;
  isbn?: string;
  pageCount?: number;
}

/** OpenAPI `StockAdjustmentRequest` */
export interface StockAdjustmentRequest {
  delta: number;
}

/** Admin list query (publishers / authors / categories) */
export interface AdminPageQuery {
  page?: number | null;
  size?: number | null;
}

/** `GET /api/admin/books` query */
export interface AdminBookQuery extends AdminPageQuery {
  status?: AdminBookStatus | null;
}

/** OpenAPI `IsbnMetadataResponse` — Open Library ham metadata */
export interface IsbnMetadataResponse {
  isbn: string;
  title?: string | null;
  description?: string | null;
  coverUrl?: string | null;
  pageCount?: number | null;
  authors: string[];
  publishers: string[];
  subjects?: string[];
}
