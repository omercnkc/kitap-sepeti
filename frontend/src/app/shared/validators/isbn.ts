/**
 * Catalog {@code Isbns} ile aynı: tire/boşluk sil, x→X; checksum ISBN-10/13.
 * Boş = geçerli (opsiyonel / PATCH temizle).
 */

export function normalizeIsbn(raw: string | null | undefined): string | null {
  if (raw == null) {
    return null;
  }
  let out = '';
  for (let i = 0; i < raw.length; i++) {
    const c = raw.charAt(i);
    if (c === '-' || /\s/.test(c)) {
      continue;
    }
    out += c === 'x' ? 'X' : c;
  }
  return out;
}

export function isValidIsbnChecksum(normalized: string): boolean {
  if (normalized.length === 10) {
    return isValidIsbn10(normalized);
  }
  if (normalized.length === 13) {
    return isValidIsbn13(normalized);
  }
  return false;
}

/** Boş/blank geçerli; doluysa normalize + checksum. */
export function isValidIsbnOptional(raw: string | null | undefined): boolean {
  if (raw == null || String(raw).trim() === '') {
    return true;
  }
  const normalized = normalizeIsbn(raw);
  return normalized != null && normalized !== '' && isValidIsbnChecksum(normalized);
}

function isValidIsbn10(isbn: string): boolean {
  let sum = 0;
  for (let i = 0; i < 10; i++) {
    const c = isbn.charAt(i);
    let digit: number;
    if (c >= '0' && c <= '9') {
      digit = c.charCodeAt(0) - 48;
    } else if (c === 'X' && i === 9) {
      digit = 10;
    } else {
      return false;
    }
    sum += digit * (10 - i);
  }
  return sum % 11 === 0;
}

function isValidIsbn13(isbn: string): boolean {
  let sum = 0;
  for (let i = 0; i < 13; i++) {
    const c = isbn.charAt(i);
    if (c < '0' || c > '9') {
      return false;
    }
    sum += (c.charCodeAt(0) - 48) * (i % 2 === 0 ? 1 : 3);
  }
  return sum % 10 === 0;
}
