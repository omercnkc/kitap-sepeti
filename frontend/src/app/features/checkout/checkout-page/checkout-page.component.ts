import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import { AccountApi } from '../../../core/api/account.api';
import { OrderApi } from '../../../core/api/order.api';
import { CartStore } from '../../../core/cart/cart.store';
import { messageForErrorCode } from '../../../core/interceptors/error-messages';
import { toProblemDetail } from '../../../core/interceptors/error.interceptor';
import {
  AddressRequest,
  AddressResponse,
  CartLineResponse,
  CartResponse,
  CheckoutRequest,
  CreateAddressRequest,
  FieldError,
} from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import {
  AddressFormComponent,
  AddressFormSaveValue,
} from '../../../shared/components/address-form/address-form.component';

@Component({
  selector: 'app-checkout-page',
  templateUrl: './checkout-page.component.html',
  styleUrls: ['./checkout-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  @ViewChild(AddressFormComponent) addressFormComp?: AddressFormComponent;

  readonly cart$: Observable<CartResponse | null> = this.cartStore.cart$;

  loading = true;
  submitting = false;
  savingAddress = false;
  showNewAddressForm = false;
  addresses: AddressResponse[] = [];
  selectedAddressId: string | null = null;
  fieldErrors: FieldError[] = [];
  formError: string | null = null;
  pendingOrderId: string | null = null;

  constructor(
    private readonly cartStore: CartStore,
    private readonly accountApi: AccountApi,
    private readonly orderApi: OrderApi,
    private readonly toast: ToastService,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.cartStore
      .load()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (cart) => {
          if (!cart || cart.itemCount === 0 || cart.items.length === 0) {
            void this.router.navigateByUrl('/cart');
            return;
          }
          this.loadAddresses();
        },
        error: () => {
          void this.router.navigateByUrl('/cart');
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get selectedAddress(): AddressResponse | null {
    if (!this.selectedAddressId) {
      return null;
    }
    return this.addresses.find((a) => a.id === this.selectedAddressId) ?? null;
  }

  get canConfirm(): boolean {
    return !!this.selectedAddress && !this.submitting && !this.loading;
  }

  /** Yeni adres modunda @ViewChild form geçerliliği */
  get isNewAddressFormValid(): boolean {
    return !!this.addressFormComp?.form?.valid;
  }

  summaryLine(address: AddressResponse): string {
    const parts = [
      address.line1,
      address.line2,
      address.district,
      address.city,
      address.postalCode,
    ].filter((p): p is string => !!p && p.trim().length > 0);
    return parts.join(', ');
  }

  trackByBookId(_index: number, line: CartLineResponse): string {
    return line.bookId;
  }

  trackByAddressId(_index: number, address: AddressResponse): string {
    return address.id;
  }

  selectAddress(id: string): void {
    this.selectedAddressId = id;
    this.showNewAddressForm = false;
    this.formError = null;
    this.pendingOrderId = null;
    this.cdr.markForCheck();
  }

  openNewAddressForm(): void {
    this.showNewAddressForm = true;
    this.formError = null;
    this.fieldErrors = [];
    this.cdr.markForCheck();
  }

  cancelNewAddressForm(): void {
    this.showNewAddressForm = false;
    this.fieldErrors = [];
    this.cdr.markForCheck();
  }

  onNewAddressSave(value: AddressFormSaveValue): void {
    if (this.savingAddress) {
      return;
    }
    if (this.addressFormComp && !this.addressFormComp.form.valid) {
      this.addressFormComp.form.markAllAsTouched();
      this.cdr.markForCheck();
      return;
    }

    this.savingAddress = true;
    this.fieldErrors = [];
    this.cdr.markForCheck();

    const body: CreateAddressRequest = {
      recipientName: value.recipientName,
      phone: value.phone,
      line1: value.line1,
      city: value.city,
      country: value.country ?? 'TR',
      isDefault: value.isDefault ?? this.addresses.length === 0,
    };
    if (value.label) {
      body.label = value.label;
    }
    if (value.line2) {
      body.line2 = value.line2;
    }
    if (value.district) {
      body.district = value.district;
    }
    if (value.postalCode) {
      body.postalCode = value.postalCode;
    }

    this.accountApi
      .createAddress(body)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.savingAddress = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (created) => {
          this.toast.success('Adres eklendi');
          this.addresses = [...this.addresses, created];
          this.selectedAddressId = created.id;
          this.showNewAddressForm = false;
          this.cdr.markForCheck();
          this.reloadAddressesKeepingSelection(created.id);
        },
        error: (err: unknown) => {
          const problem = toProblemDetail(err);
          this.fieldErrors = problem.errors ?? [];
        },
      });
  }

  onConfirm(): void {
    if (this.submitting) {
      return;
    }
    this.formError = null;
    this.pendingOrderId = null;

    const address = this.selectedAddress;
    if (!address) {
      if (this.showNewAddressForm) {
        if (!this.addressFormComp?.form.valid) {
          this.addressFormComp?.form.markAllAsTouched();
          this.formError = 'Teslimat adresi için formu tamamlayın.';
          this.cdr.markForCheck();
          return;
        }
        this.formError = 'Önce yeni adresi kaydedin, sonra onaylayın.';
      } else {
        this.formError = 'Teslimat adresi seçin.';
      }
      this.cdr.markForCheck();
      return;
    }

    const body: CheckoutRequest = {
      address: this.toCheckoutAddress(address),
    };

    this.submitting = true;
    this.cdr.markForCheck();

    this.orderApi
      .checkout(body)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.submitting = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (order) => {
          this.cartStore.load().subscribe({
            error: () => this.cartStore.reset(),
          });
          void this.router.navigate(['/orders', order.id]);
        },
        error: (err: unknown) => {
          const problem = toProblemDetail(err);
          if (problem.code === 'ORDER_PENDING_EXISTS') {
            this.pendingOrderId = problem.orderId ?? null;
            this.formError =
              messageForErrorCode(problem.code) ??
              'Bekleyen bir siparişiniz var.';
            return;
          }
          this.formError =
            messageForErrorCode(problem.code) ??
            problem.detail ??
            problem.title ??
            'Sipariş oluşturulamadı.';
        },
      });
  }

  private loadAddresses(): void {
    this.accountApi
      .listAddresses()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.loading = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (list) => {
          this.addresses = list;
          const def = list.find((a) => a.isDefault) ?? list[0] ?? null;
          this.selectedAddressId = def?.id ?? null;
          this.showNewAddressForm = list.length === 0;
          this.cdr.markForCheck();
        },
        error: () => {
          this.formError = 'Adresler yüklenemedi.';
          this.showNewAddressForm = true;
        },
      });
  }

  private reloadAddressesKeepingSelection(preferredId: string): void {
    this.accountApi
      .listAddresses()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (list) => {
          this.addresses = list;
          this.selectedAddressId =
            list.find((a) => a.id === preferredId)?.id ??
            list.find((a) => a.isDefault)?.id ??
            list[0]?.id ??
            null;
          this.cdr.markForCheck();
        },
      });
  }

  private toCheckoutAddress(address: AddressResponse): AddressRequest {
    const body: AddressRequest = {
      recipientName: address.recipientName,
      phone: address.phone,
      line1: address.line1,
      city: address.city,
      country: address.country || 'TR',
    };
    if (address.line2) {
      body.line2 = address.line2;
    }
    if (address.district) {
      body.district = address.district;
    }
    if (address.postalCode) {
      body.postalCode = address.postalCode;
    }
    return body;
  }
}
