import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { isValidIsbnOptional, normalizeIsbn } from './isbn';

/**
 * Boş geçerli; doluysa ISBN-10/13 checksum.
 * `allowedValue`: DB’den yüklenen (checksum’sız seed) değeri olduğu gibi kabul eder —
 * kullanıcı alanı değiştirene kadar form kilitlenmesin.
 * Hata: `isbn`.
 */
export function isbnValidator(allowedValue?: () => string | null | undefined): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const raw = control.value == null ? '' : String(control.value);
    if (isValidIsbnOptional(raw)) {
      return null;
    }
    if (allowedValue) {
      const allowed = allowedValue();
      if (allowed != null && String(allowed).trim() !== '') {
        const current = normalizeIsbn(raw);
        const previous = normalizeIsbn(String(allowed));
        if (current != null && previous != null && current === previous) {
          return null;
        }
      }
    }
    return { isbn: true };
  };
}
