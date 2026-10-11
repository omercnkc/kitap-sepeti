/** OpenAPI `AuthorRef` */
export interface AuthorRef {
  id: string;
  name: string;
  slug: string;
}

/** OpenAPI `CategoryRef` */
export interface CategoryRef {
  id: string;
  name: string;
  slug: string;
}

/** OpenAPI `BookSummaryResponse` */
export interface BookSummary {
  id: string;
  title: string;
  authors: AuthorRef[];
  priceAmount: number;
  currency: string;
  inStock: boolean;
  coverUrl?: string;
}

/** OpenAPI `BookDetailResponse` */
export interface BookDetail {
  id: string;
  title: string;
  authors: AuthorRef[];
  categories: CategoryRef[];
  priceAmount: number;
  currency: string;
  inStock: boolean;
  coverUrl?: string;
  description?: string;
  isbn?: string;
  pageCount?: number;
  publishedAt?: string;
}

/** OpenAPI `CategoryRef` — public `GET /api/categories` öğesi */
export interface CategoryRef {
  id: string;
  name: string;
  slug: string;
}

/** `GET /api/books` query parametreleri (OpenAPI path parameters) */
export interface BookFilter {
  q?: string | null;
  categoryId?: string | null;
  authorId?: string | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  sort?: string | null;
  page?: number | null;
  size?: number | null;
}
