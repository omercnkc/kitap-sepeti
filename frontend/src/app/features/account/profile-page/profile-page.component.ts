import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import { AuthService } from '../../../core/auth/auth.service';
import { messageForErrorCode } from '../../../core/interceptors/error-messages';
import { toProblemDetail } from '../../../core/interceptors/error.interceptor';
import { FieldError, UpdateProfileRequest, UserResponse } from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { normalizeTrPhone } from '../../../shared/validators/tr-phone';
import { trPhoneValidator } from '../../../shared/validators/tr-phone.validator';

@Component({
  selector: 'app-profile-page',
  templateUrl: './profile-page.component.html',
  styleUrls: ['./profile-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  readonly form: FormGroup = this.fb.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    phone: ['', [trPhoneValidator()]],
  });

  loading = true;
  submitting = false;
  formError: string | null = null;
  fieldErrors: FieldError[] = [];
  email = '';

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: AuthService,
    private readonly toast: ToastService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.auth
      .getMe()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.loading = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (user) => this.applyUser(user),
        error: () => {
          this.formError = 'Profil yüklenemedi.';
        },
      });
  }

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
      phone: string;
    };

    // Kısmi PATCH: gönderilen alanlar uygulanır; phone "" = sil; doluysa kanonik 5xxxxxxxxx.
    const phoneRaw = (raw.phone ?? '').trim();
    const body: UpdateProfileRequest = {
      firstName: raw.firstName.trim(),
      lastName: raw.lastName.trim(),
      phone: phoneRaw ? normalizeTrPhone(phoneRaw)! : '',
    };

    this.submitting = true;
    this.cdr.markForCheck();

    this.auth
      .updateProfile(body)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.submitting = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (user) => {
          this.applyUser(user);
          this.toast.success('Profil güncellendi');
        },
        error: (err: unknown) => {
          this.applyProblem(err);
        },
      });
  }

  private applyUser(user: UserResponse): void {
    this.email = user.email;
    this.form.reset({
      firstName: user.firstName,
      lastName: user.lastName,
      phone: user.phone ?? '',
    });
    this.cdr.markForCheck();
  }

  private applyProblem(err: unknown): void {
    const problem = toProblemDetail(err);
    this.fieldErrors = problem.errors ?? [];
    if (this.fieldErrors.length > 0) {
      this.formError = 'Girdiğiniz bilgileri kontrol edin.';
      return;
    }
    this.formError =
      messageForErrorCode(problem.code) ??
      problem.detail ??
      problem.title ??
      'Profil güncellenemedi.';
  }
}
