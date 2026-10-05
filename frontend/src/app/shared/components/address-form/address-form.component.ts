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
import { AddressResponse, CreateAddressRequest, FieldError } from '../../../core/models';

/** AddressForm kaydet çıktısı — create/update istek gövdesine dönüştürülür. */
export type AddressFormSaveValue = CreateAddressRequest;

@Component({
  selector: 'app-address-form',
  templateUrl: './address-form.component.html',
  styleUrls: ['./address-form.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddressFormComponent implements OnChanges, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  @Input() address: AddressResponse | null = null;
  @Input() fieldErrors: FieldError[] = [];
  @Input() submitting = false;
  @Input() showCancel = true;

  @Output() readonly save = new EventEmitter<AddressFormSaveValue>();
  @Output() readonly cancel = new EventEmitter<void>();

  readonly form: FormGroup = this.fb.group({
    label: [''],
    recipientName: ['', [Validators.required]],
    phone: ['', [Validators.required]],
    line1: ['', [Validators.required]],
    line2: [''],
    district: [''],
    city: ['', [Validators.required]],
    postalCode: [''],
    country: ['TR', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]],
    isDefault: [false],
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
    if (changes['address']) {
      this.patchFromAddress(this.address);
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

    const raw = this.form.getRawValue() as {
      label: string;
      recipientName: string;
      phone: string;
      line1: string;
      line2: string;
      district: string;
      city: string;
      postalCode: string;
      country: string;
      isDefault: boolean;
    };

    const body: AddressFormSaveValue = {
      recipientName: raw.recipientName.trim(),
      phone: raw.phone.trim(),
      line1: raw.line1.trim(),
      city: raw.city.trim(),
      country: (raw.country || 'TR').trim().toUpperCase(),
      isDefault: !!raw.isDefault,
    };

    const label = raw.label.trim();
    if (label) {
      body.label = label;
    }
    const line2 = raw.line2.trim();
    if (line2) {
      body.line2 = line2;
    }
    const district = raw.district.trim();
    if (district) {
      body.district = district;
    }
    const postalCode = raw.postalCode.trim();
    if (postalCode) {
      body.postalCode = postalCode;
    }

    this.save.emit(body);
  }

  onCancel(): void {
    this.cancel.emit();
  }

  private patchFromAddress(address: AddressResponse | null): void {
    if (!address) {
      this.form.reset({
        label: '',
        recipientName: '',
        phone: '',
        line1: '',
        line2: '',
        district: '',
        city: '',
        postalCode: '',
        country: 'TR',
        isDefault: false,
      });
      return;
    }

    this.form.reset({
      label: address.label ?? '',
      recipientName: address.recipientName,
      phone: address.phone,
      line1: address.line1,
      line2: address.line2 ?? '',
      district: address.district ?? '',
      city: address.city,
      postalCode: address.postalCode ?? '',
      country: address.country || 'TR',
      isDefault: address.isDefault,
    });
  }
}
