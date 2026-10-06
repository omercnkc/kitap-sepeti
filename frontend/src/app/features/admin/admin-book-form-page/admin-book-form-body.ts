import { CreateBookRequest, UpdateBookRequest } from '../../../core/models';
import { normalizeIsbn } from '../../../shared/validators/isbn';

/** API Digits(10,2) — float gürültüsünü kes. */
export function roundMoney2(value: number): number {
  return Math.round(value * 100) / 100;
}

export interface AdminBookFormRaw {
  title: string;
  publisherName: string;
  priceAmount: number | string | null;
  authorNames: string[];
  categoryIds: string[];
  coverUrl: string;
  description: string;
  isbn: string;
  pageCount: number | string | null;
  initialStock?: number | string | null;
  version?: number | string | null;
}

export interface BuildUpdateBookBodyOptions {
  /** Yüklenen kayıttaki ISBN; değişmediyse PATCH’e hiç koyma (Bean Validation tekrarlamasın). */
  previousIsbn?: string | null;
}

/**
 * Create: boş isbn/cover/description hiç gönderilmez.
 * Fiyat 2 ondalığa yuvarlanır; ISBN normalize edilir.
 */
export function buildCreateBookBody(raw: AdminBookFormRaw): CreateBookRequest {
  const body: CreateBookRequest = {
    title: String(raw.title).trim(),
    publisherName: String(raw.publisherName).trim(),
    priceAmount: roundMoney2(Number(raw.priceAmount)),
  };
  if (raw.authorNames?.length) {
    body.authorNames = raw.authorNames;
  }
  if (raw.categoryIds?.length) {
    body.categoryIds = raw.categoryIds;
  }
  const coverUrl = String(raw.coverUrl || '').trim();
  if (coverUrl) {
    body.coverUrl = coverUrl;
  }
  const description = String(raw.description || '').trim();
  if (description) {
    body.description = description;
  }
  const isbnRaw = String(raw.isbn || '').trim();
  if (isbnRaw) {
    body.isbn = normalizeIsbn(isbnRaw) || isbnRaw;
  }
  if (raw.pageCount != null && raw.pageCount !== '') {
    body.pageCount = Number(raw.pageCount);
  }
  if (raw.initialStock != null && raw.initialStock !== '') {
    body.initialStock = Number(raw.initialStock);
  }
  return body;
}

/**
 * Update: boş isbn/cover/description → `""` (backend temizler).
 * ISBN yüklenenle aynıysa alan gönderilmez (geçersiz seed ISBN kaydı bozmasın).
 * Fiyat 2 ondalığa yuvarlanır; ISBN normalize edilir.
 */
export function buildUpdateBookBody(
  raw: AdminBookFormRaw,
  options?: BuildUpdateBookBodyOptions,
): UpdateBookRequest {
  const body: UpdateBookRequest = {
    version: Number(raw.version),
    title: String(raw.title).trim(),
    publisherName: String(raw.publisherName).trim(),
    priceAmount: roundMoney2(Number(raw.priceAmount)),
    authorNames: raw.authorNames ?? [],
    categoryIds: raw.categoryIds ?? [],
    coverUrl: String(raw.coverUrl || '').trim(),
    description: String(raw.description || '').trim(),
  };
  const isbnRaw = String(raw.isbn || '').trim();
  if (!isbnRaw) {
    body.isbn = '';
  } else {
    const normalized = normalizeIsbn(isbnRaw) || isbnRaw;
    const previousRaw = options?.previousIsbn;
    const previous =
      previousRaw != null && String(previousRaw).trim() !== ''
        ? normalizeIsbn(String(previousRaw)) || String(previousRaw).trim()
        : null;
    if (previous == null || normalized !== previous) {
      body.isbn = normalized;
    }
  }
  if (raw.pageCount != null && raw.pageCount !== '') {
    body.pageCount = Number(raw.pageCount);
  }
  return body;
}
