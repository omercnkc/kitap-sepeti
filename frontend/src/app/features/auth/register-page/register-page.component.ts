import { ChangeDetectionStrategy, Component } from '@angular/core';
// TODO (öğrenme): FormBuilder, FormGroup, Validators, AuthService, Router import et

/**
 * KAYIT FORMU — İSKELET ONLY
 * Bu sayfadaki Reactive Form + submit + errors[] eşlemesini SEN dolduracaksın.
 * Örnek kalıp: `login-page/login-page.component.ts`
 *
 * OpenAPI RegisterRequest alanları:
 * - firstName (zorunlu)
 * - lastName (zorunlu)
 * - email (zorunlu)
 * - password (zorunlu, 8–72)
 * - phone (opsiyonel)
 *
 * Başarıda: AuthService.register → /books
 * Hata: ProblemDetail.errors[] → alan bazlı (app-field-error)
 */
@Component({
  selector: 'app-register-page',
  templateUrl: './register-page.component.html',
  styleUrls: ['./register-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPageComponent {
  // TODO: FormGroup oluştur (firstName, lastName, email, password, phone?)
  // form!: FormGroup;

  // TODO: submitting / formError / fieldErrors state

  // TODO: onSubmit() → AuthService.register(...) → navigate /books
  // TODO: VALIDATION_FAILED / EMAIL_ALREADY_EXISTS hatalarını işle
}
