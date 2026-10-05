import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { isValidTrPhone } from './tr-phone';

/** Doluysa TR cep; boş geçerli. Hata anahtarı: `trPhone`. */
export function trPhoneValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const raw = control.value;
    if (isValidTrPhone(raw == null ? '' : String(raw))) {
      return null;
    }
    return { trPhone: true };
  };
}
