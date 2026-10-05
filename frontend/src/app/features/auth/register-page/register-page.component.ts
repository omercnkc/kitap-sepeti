import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AuthService } from '../../../core/auth/auth.service';
import { messageForErrorCode } from '../../../core/interceptors/error-messages';
import { toProblemDetail } from '../../../core/interceptors/error.interceptor';
import { FieldError, RegisterRequest } from '../../../core/models';

@Component({
  selector: 'app-register-page',
  templateUrl: './register-page.component.html',
  styleUrls: ['./register-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPageComponent implements OnDestroy {
  private readonly destroy$ = new Subject<void>();

  readonly form: FormGroup = this.fb.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    phone: [''],
    rememberMe: [false],
  });

  submitting = false;
  formError: string | null = null;
  fieldErrors: FieldError[] = [];

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSubmit(): void {
    this.formError = null;
    this.fieldErrors = [];
    this.form.markAllAsTouched();

    if (this.form.invalid || this.submitting) {
      this.cdr.markForCheck();
      return;
    }

    const raw = this.form.getRawValue() as {
      firstName: string;
      lastName: string;
      email: string;
      password: string;
      phone: string;
      rememberMe: boolean;
    };

    const request: RegisterRequest = {
      firstName: raw.firstName.trim(),
      lastName: raw.lastName.trim(),
      email: raw.email.trim(),
      password: raw.password,
    };
    const phone = (raw.phone ?? '').trim();
    if (phone) {
      request.phone = phone;
    }

    this.submitting = true;
    this.cdr.markForCheck();

    this.auth
      .register(request, !!raw.rememberMe)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.submitting = false;
          void this.router.navigateByUrl('/books');
        },
        error: (err: unknown) => {
          this.submitting = false;
          this.applyProblem(err);
          this.cdr.markForCheck();
        },
      });
  }

  private applyProblem(err: unknown): void {
    const problem = toProblemDetail(err);
    this.fieldErrors = problem.errors ?? [];

    if (problem.code === 'EMAIL_ALREADY_EXISTS') {
      this.formError =
        messageForErrorCode(problem.code) ?? 'Bu e-posta zaten kayıtlı.';
      return;
    }

    if (this.fieldErrors.length > 0) {
      this.formError = 'Girdiğiniz bilgileri kontrol edin.';
      return;
    }

    this.formError =
      messageForErrorCode(problem.code) ??
      problem.detail ??
      problem.title ??
      'Kayıt başarısız.';
  }
}
