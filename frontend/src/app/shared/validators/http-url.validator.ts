import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Catalog {@code @HttpUrl}: boş geçerli; doluysa mutlak http(s):// + host.
 * Hata: `httpUrl`.
 */
export function httpUrlValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const raw = control.value == null ? '' : String(control.value).trim();
    if (!raw) {
      return null;
    }
    try {
      const url = new URL(raw);
      if (url.protocol !== 'http:' && url.protocol !== 'https:') {
        return { httpUrl: true };
      }
      if (!url.hostname) {
        return { httpUrl: true };
      }
      return null;
    } catch {
      return { httpUrl: true };
    }
  };
}
