/**
 * TR cep telefonu: yalnız rakamlar; baştaki 90 veya 0 temizlenir → ^5\d{9}$.
 * API’ye giden kanonik format: 10 hane, 5 ile başlar (örn. 5551112233).
 */

/** Yazarken izin verilen en fazla rakam: `90` + 10 hane cep. */
export const TR_PHONE_MAX_DIGITS = 12;

/** Boşluklu yazım için üst sınır (örn. +90 555 123 45 67). */
export const TR_PHONE_MAX_LENGTH = 16;

/**
 * Input filtre: yalnız isteğe bağlı baştaki `+`, rakam ve boşluk.
 * Harf / özel karakter atılır; rakam sayısı {@link TR_PHONE_MAX_DIGITS} ile sınırlanır.
 */
export function filterTrPhoneInput(raw: string | null | undefined): string {
  if (raw == null || raw === '') {
    return '';
  }

  let out = '';
  let digitCount = 0;

  for (let i = 0; i < raw.length; i++) {
    const ch = raw.charAt(i);
    if (ch === '+' && out.length === 0) {
      out += '+';
      continue;
    }
    if (ch === ' ') {
      if (out.length > 0 && out.charAt(out.length - 1) !== ' ') {
        out += ' ';
      }
      continue;
    }
    if (ch >= '0' && ch <= '9') {
      if (digitCount >= TR_PHONE_MAX_DIGITS) {
        continue;
      }
      out += ch;
      digitCount++;
    }
  }

  if (out.length > TR_PHONE_MAX_LENGTH) {
    out = out.slice(0, TR_PHONE_MAX_LENGTH);
  }
  return out;
}

export function normalizeTrPhone(value: string | null | undefined): string | null {
  if (value == null) {
    return null;
  }
  const trimmed = String(value).trim();
  if (!trimmed) {
    return null;
  }

  let digits = trimmed.replace(/\D/g, '');
  if (digits.startsWith('90') && digits.length === 12) {
    digits = digits.slice(2);
  } else if (digits.startsWith('0') && digits.length === 11) {
    digits = digits.slice(1);
  }

  return /^5\d{9}$/.test(digits) ? digits : null;
}

/** Boş/blank geçerli (required ayrı); doluysa normalize + ^5\\d{9}$. */
export function isValidTrPhone(value: string | null | undefined): boolean {
  if (value == null || String(value).trim() === '') {
    return true;
  }
  return normalizeTrPhone(value) !== null;
}
