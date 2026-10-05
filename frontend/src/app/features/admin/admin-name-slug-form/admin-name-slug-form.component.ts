import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
} from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { FieldError } from '../../../core/models';

const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

export interface AdminNameSlugValue {
  name: string;
  slug?: string;
}

@Component({
  selector: 'app-admin-name-slug-form',
  templateUrl: './admin-name-slug-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminNameSlugFormComponent implements OnChanges, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  @Input() name = '';
  @Input() slug = '';
  @Input() fieldErrors: FieldError[] = [];
  @Input() submitting = false;
  @Input() submitLabel = 'Kaydet';

  @Output() readonly save = new EventEmitter<AdminNameSlugValue>();
  @Output() readonly cancel = new EventEmitter<void>();

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    slug: ['', [Validators.pattern(SLUG_PATTERN), Validators.maxLength(160)]],
  });

  constructor(
    private readonly fb: FormBuilder,
    private readonly cdr: ChangeDetectorRef,
  ) {
    this.form.statusChanges.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.cdr.markForCheck();
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['name'] || changes['slug']) {
      this.form.reset({
        name: this.name || '',
        slug: this.slug || '',
      });
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get canSave(): boolean {
    return this.form.valid && !this.submitting;
  }

  onSubmit(): void {
    this.form.markAllAsTouched();
    this.cdr.markForCheck();
    if (!this.canSave) {
      return;
    }
    const raw = this.form.getRawValue() as { name: string; slug: string };
    const name = raw.name.trim();
    const slug = raw.slug.trim();
    const value: AdminNameSlugValue = { name };
    if (slug) {
      value.slug = slug;
    }
    this.save.emit(value);
  }

  onCancel(): void {
    this.cancel.emit();
  }
}
