import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AuthService } from '../../../core/auth/auth.service';
import { messageForErrorCode } from '../../../core/interceptors/error-messages';
import { FieldError, ProblemDetail } from '../../../core/models';

@Component({
  selector: 'app-login-page',
  templateUrl: './login-page.component.html',
  styleUrls: ['./login-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPageComponent implements OnDestroy {
  private readonly destroy$ = new Subject<void>();

  readonly form: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
    rememberMe: [true],
  });

  submitting = false;
  formError: string | null = null;
  fieldErrors: FieldError[] = [];

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
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

    const { email, password, rememberMe } = this.form.getRawValue() as {
      email: string;
      password: string;
      rememberMe: boolean;
    };

    this.submitting = true;
    this.cdr.markForCheck();

    this.auth
      .login(email, password, !!rememberMe)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.submitting = false;
          void this.router.navigateByUrl(this.resolveReturnUrl());
        },
        error: (err: unknown) => {
          this.submitting = false;
          this.applyProblem(err);
          this.cdr.markForCheck();
        },
      });
  }

  private resolveReturnUrl(): string {
    const raw = this.route.snapshot.queryParamMap.get('returnUrl');
    if (raw && raw.startsWith('/') && !raw.startsWith('//')) {
      return raw;
    }
    return '/books';
  }

  private applyProblem(err: unknown): void {
    if (!err || typeof err !== 'object') {
      this.formError = 'Giriş başarısız.';
      return;
    }
    const problem = err as ProblemDetail;
    this.fieldErrors = problem.errors ?? [];

    if (problem.status === 401) {
      this.formError =
        messageForErrorCode(problem.code) ?? 'E-posta veya parola hatalı.';
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
      'Giriş başarısız.';
  }
}
