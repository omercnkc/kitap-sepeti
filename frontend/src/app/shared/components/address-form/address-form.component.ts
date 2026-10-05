import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { TrAddressDataService } from '../../../core/geo/tr-address-data.service';
import { TrIl, TrIlce, TrMahalle } from '../../../core/geo/tr-address.models';
import { AddressResponse, CreateAddressRequest, FieldError } from '../../../core/models';

/** AddressForm kaydet çıktısı — create/update istek gövdesine dönüştürülür. */
export type AddressFormSaveValue = CreateAddressRequest;

function normalizeTr(value: string | null | undefined): string {
  return (value ?? '').trim().toLocaleLowerCase('tr-TR');
}

@Component({
  selector: 'app-address-form',
  templateUrl: './address-form.component.html',
  styleUrls: ['./address-form.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddressFormComponent implements OnInit, OnChanges, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private suppressCascade = false;
  private pendingAddress: AddressResponse | null = null;

  @Input() address: AddressResponse | null = null;
  @Input() fieldErrors: FieldError[] = [];
  @Input() submitting = false;
  @Input() showCancel = true;

  @Output() readonly save = new EventEmitter<AddressFormSaveValue>();
  @Output() readonly cancel = new EventEmitter<void>();

  iller: TrIl[] = [];
  ilceler: TrIlce[] = [];
  mahalleler: TrMahalle[] = [];

  illerLoading = false;
  ilcelerLoading = false;
  mahallelerLoading = false;

  /** Kayıtlı string’ler select ile eşleşmezse serbest metin + uyarı. */
  freeTextMode = false;
  geoMismatchMessage: string | null = null;

  readonly form: FormGroup = this.fb.group({
    label: [''],
    recipientName: ['', [Validators.required]],
    phone: ['', [Validators.required]],
    sehirId: [''],
    ilceId: [''],
    mahalleId: [''],
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
    private readonly trAddressData: TrAddressDataService,
  ) {
    this.form.statusChanges.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.cdr.markForCheck();
    });
  }

  ngOnInit(): void {
    this.applySelectValidators();
    this.illerLoading = true;
    this.trAddressData
      .getIller()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (iller) => {
          this.iller = iller;
          this.illerLoading = false;
          this.cdr.markForCheck();
          if (this.pendingAddress) {
            void this.patchFromAddress(this.pendingAddress);
            this.pendingAddress = null;
          }
        },
        error: () => {
          this.illerLoading = false;
          this.freeTextMode = true;
          this.geoMismatchMessage =
            'İl listesi yüklenemedi; adresi serbest metin olarak girin.';
          this.applySelectValidators();
          this.cdr.markForCheck();
        },
      });

    this.form
      .get('sehirId')!
      .valueChanges.pipe(takeUntil(this.destroy$))
      .subscribe((sehirId: string) => {
        if (this.suppressCascade || this.freeTextMode) {
          return;
        }
        this.onSehirSelected(sehirId, true);
      });

    this.form
      .get('ilceId')!
      .valueChanges.pipe(takeUntil(this.destroy$))
      .subscribe((ilceId: string) => {
        if (this.suppressCascade || this.freeTextMode) {
          return;
        }
        this.onIlceSelected(ilceId, true);
      });

    this.form
      .get('mahalleId')!
      .valueChanges.pipe(takeUntil(this.destroy$))
      .subscribe((mahalleId: string) => {
        if (this.suppressCascade || this.freeTextMode) {
          return;
        }
        this.onMahalleSelected(mahalleId);
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['address']) {
      if (this.iller.length === 0 && this.illerLoading) {
        this.pendingAddress = this.address;
        return;
      }
      void this.patchFromAddress(this.address);
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

  useSelectMode(): void {
    this.freeTextMode = false;
    this.geoMismatchMessage = null;
    this.applySelectValidators();
    this.withSuppressedCascade(() => {
      this.form.patchValue({
        sehirId: '',
        ilceId: '',
        mahalleId: '',
        city: '',
        district: '',
        line1: '',
      });
    });
    this.ilceler = [];
    this.mahalleler = [];
    this.cdr.markForCheck();
  }

  private async patchFromAddress(address: AddressResponse | null): Promise<void> {
    if (!address) {
      this.freeTextMode = false;
      this.geoMismatchMessage = null;
      this.applySelectValidators();
      this.withSuppressedCascade(() => {
        this.form.reset({
          label: '',
          recipientName: '',
          phone: '',
          sehirId: '',
          ilceId: '',
          mahalleId: '',
          line1: '',
          line2: '',
          district: '',
          city: '',
          postalCode: '',
          country: 'TR',
          isDefault: false,
        });
      });
      this.ilceler = [];
      this.mahalleler = [];
      this.cdr.markForCheck();
      return;
    }

    this.withSuppressedCascade(() => {
      this.form.reset({
        label: address.label ?? '',
        recipientName: address.recipientName,
        phone: address.phone,
        sehirId: '',
        ilceId: '',
        mahalleId: '',
        line1: address.line1,
        line2: address.line2 ?? '',
        district: address.district ?? '',
        city: address.city,
        postalCode: address.postalCode ?? '',
        country: address.country || 'TR',
        isDefault: address.isDefault,
      });
    });

    if (this.iller.length === 0) {
      this.enterFreeTextMode(
        'İl listesi henüz yüklenmedi; kayıtlı değerler serbest metin olarak gösteriliyor.',
      );
      return;
    }

    const il = this.findIl(address.city);
    if (!il) {
      this.enterFreeTextMode(
        'Kayıtlı il listede bulunamadı; serbest metin olarak düzenleyebilir veya yeniden seçebilirsiniz.',
      );
      return;
    }

    this.freeTextMode = false;
    this.geoMismatchMessage = null;
    this.applySelectValidators();

    this.withSuppressedCascade(() => {
      this.form.patchValue({ sehirId: il.sehir_id, city: il.sehir_adi });
    });

    await this.loadIlcelerFor(il.sehir_id);
    const ilce = this.findIlce(address.district);
    if (!ilce) {
      this.geoMismatchMessage = address.district
        ? 'Kayıtlı ilçe listede bulunamadı; ilçeyi yeniden seçin.'
        : null;
      this.withSuppressedCascade(() => {
        this.form.patchValue({ ilceId: '', mahalleId: '', district: address.district ?? '' });
      });
      // line1 eşleşemez; kullanıcı mahalle seçince üzerine yazar
      this.applySelectValidators();
      this.cdr.markForCheck();
      return;
    }

    this.withSuppressedCascade(() => {
      this.form.patchValue({ ilceId: ilce.ilce_id, district: ilce.ilce_adi });
    });

    await this.loadMahallelerFor(ilce.ilce_id);
    const mahalle = this.findMahalle(address.line1);
    if (!mahalle) {
      this.geoMismatchMessage =
        'Kayıtlı mahalle listede bulunamadı; mahalleyi yeniden seçin veya satır 1 metnini koruyun.';
      this.withSuppressedCascade(() => {
        this.form.patchValue({ mahalleId: '', line1: address.line1 });
      });
      // mahalle zorunlu select — eşleşmezse line1 metnini koru, mahalleId boş bırak
      this.form.get('mahalleId')!.clearValidators();
      this.form.get('mahalleId')!.updateValueAndValidity({ emitEvent: false });
      this.cdr.markForCheck();
      return;
    }

    this.withSuppressedCascade(() => {
      this.form.patchValue({ mahalleId: mahalle.mahalle_id, line1: mahalle.mahalle_adi });
    });
    this.cdr.markForCheck();
  }

  private onSehirSelected(sehirId: string, resetDependents: boolean): void {
    if (resetDependents) {
      this.withSuppressedCascade(() => {
        this.form.patchValue({
          ilceId: '',
          mahalleId: '',
          district: '',
          line1: '',
          city: '',
        });
      });
      this.ilceler = [];
      this.mahalleler = [];
    }

    if (!sehirId) {
      this.cdr.markForCheck();
      return;
    }

    const il = this.iller.find((row) => row.sehir_id === sehirId);
    if (il) {
      this.form.patchValue({ city: il.sehir_adi }, { emitEvent: false });
    }
    void this.loadIlcelerFor(sehirId);
  }

  private onIlceSelected(ilceId: string, resetDependents: boolean): void {
    if (resetDependents) {
      this.withSuppressedCascade(() => {
        this.form.patchValue({ mahalleId: '', line1: '', district: '' });
      });
      this.mahalleler = [];
    }

    if (!ilceId) {
      this.cdr.markForCheck();
      return;
    }

    const ilce = this.ilceler.find((row) => row.ilce_id === ilceId);
    if (ilce) {
      this.form.patchValue({ district: ilce.ilce_adi }, { emitEvent: false });
    }
    void this.loadMahallelerFor(ilceId);
  }

  private onMahalleSelected(mahalleId: string): void {
    if (!mahalleId) {
      this.form.patchValue({ line1: '' }, { emitEvent: false });
      this.cdr.markForCheck();
      return;
    }
    const mahalle = this.mahalleler.find((row) => row.mahalle_id === mahalleId);
    if (mahalle) {
      this.form.patchValue({ line1: mahalle.mahalle_adi }, { emitEvent: false });
      this.geoMismatchMessage = null;
      this.applySelectValidators();
    }
    this.cdr.markForCheck();
  }

  private loadIlcelerFor(sehirId: string): Promise<void> {
    this.ilcelerLoading = true;
    this.cdr.markForCheck();
    return new Promise((resolve) => {
      this.trAddressData
        .getIlceler(sehirId)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (rows) => {
            this.ilceler = rows;
            this.ilcelerLoading = false;
            this.cdr.markForCheck();
            resolve();
          },
          error: () => {
            this.ilceler = [];
            this.ilcelerLoading = false;
            this.cdr.markForCheck();
            resolve();
          },
        });
    });
  }

  private loadMahallelerFor(ilceId: string): Promise<void> {
    this.mahallelerLoading = true;
    this.cdr.markForCheck();
    return new Promise((resolve) => {
      this.trAddressData
        .getMahalleler(ilceId)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (rows) => {
            this.mahalleler = rows;
            this.mahallelerLoading = false;
            this.cdr.markForCheck();
            resolve();
          },
          error: () => {
            this.mahalleler = [];
            this.mahallelerLoading = false;
            this.cdr.markForCheck();
            resolve();
          },
        });
    });
  }

  private findIl(city: string): TrIl | undefined {
    const target = normalizeTr(city);
    return this.iller.find((row) => normalizeTr(row.sehir_adi) === target);
  }

  private findIlce(district: string | null | undefined): TrIlce | undefined {
    if (!district) {
      return undefined;
    }
    const target = normalizeTr(district);
    return this.ilceler.find((row) => normalizeTr(row.ilce_adi) === target);
  }

  private findMahalle(line1: string): TrMahalle | undefined {
    const target = normalizeTr(line1);
    return this.mahalleler.find((row) => normalizeTr(row.mahalle_adi) === target);
  }

  private enterFreeTextMode(message: string): void {
    this.freeTextMode = true;
    this.geoMismatchMessage = message;
    this.applySelectValidators();
    this.cdr.markForCheck();
  }

  private applySelectValidators(): void {
    const sehirId = this.form.get('sehirId')!;
    const ilceId = this.form.get('ilceId')!;
    const mahalleId = this.form.get('mahalleId')!;

    if (this.freeTextMode) {
      sehirId.clearValidators();
      ilceId.clearValidators();
      mahalleId.clearValidators();
    } else {
      sehirId.setValidators([Validators.required]);
      ilceId.setValidators([Validators.required]);
      mahalleId.setValidators([Validators.required]);
    }

    sehirId.updateValueAndValidity({ emitEvent: false });
    ilceId.updateValueAndValidity({ emitEvent: false });
    mahalleId.updateValueAndValidity({ emitEvent: false });
  }

  private withSuppressedCascade(fn: () => void): void {
    this.suppressCascade = true;
    try {
      fn();
    } finally {
      this.suppressCascade = false;
    }
  }
}
