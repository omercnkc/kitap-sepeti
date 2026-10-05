import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { finalize, switchMap, takeUntil } from 'rxjs/operators';
import { AccountApi } from '../../../core/api/account.api';
import { toProblemDetail } from '../../../core/interceptors/error.interceptor';
import {
  AddressResponse,
  CreateAddressRequest,
  FieldError,
  UpdateAddressRequest,
} from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { AddressFormSaveValue } from '../../../shared/components/address-form/address-form.component';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

type PageState = 'loading' | 'ready' | 'notFound' | 'error';

@Component({
  selector: 'app-addresses-page',
  templateUrl: './addresses-page.component.html',
  styleUrls: ['./addresses-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddressesPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private modalRef: NgbModalRef | null = null;

  @ViewChild('addressModal') addressModal!: TemplateRef<unknown>;

  state: PageState = 'loading';
  addresses: AddressResponse[] = [];
  editingAddress: AddressResponse | null = null;
  saving = false;
  busyId: string | null = null;
  fieldErrors: FieldError[] = [];

  constructor(
    private readonly accountApi: AccountApi,
    private readonly modal: NgbModal,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly toast: ToastService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        takeUntil(this.destroy$),
        switchMap((params) => {
          const id = params.get('id');
          this.state = 'loading';
          this.cdr.markForCheck();

          if (!id) {
            this.editingAddress = null;
            return this.accountApi.listAddresses();
          }

          return this.accountApi.getAddress(id).pipe(
            switchMap((address) => {
              this.editingAddress = address;
              return this.accountApi.listAddresses();
            }),
          );
        }),
      )
      .subscribe({
        next: (list) => {
          this.addresses = list;
          this.state = 'ready';
          this.cdr.markForCheck();
          if (this.editingAddress && this.route.snapshot.paramMap.get('id')) {
            // Deep-link düzenleme: liste hazır olunca modal aç
            queueMicrotask(() => this.openEditModal(this.editingAddress as AddressResponse));
          }
        },
        error: (err: unknown) => {
          const problem = toProblemDetail(err);
          if (problem.status === 404) {
            this.state = 'notFound';
          } else {
            this.state = 'error';
          }
          this.cdr.markForCheck();
        },
      });
  }

  ngOnDestroy(): void {
    this.modalRef?.dismiss();
    this.destroy$.next();
    this.destroy$.complete();
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

  trackById(_index: number, address: AddressResponse): string {
    return address.id;
  }

  openCreate(): void {
    this.editingAddress = null;
    this.fieldErrors = [];
    this.openModal();
  }

  openEdit(address: AddressResponse): void {
    this.openEditModal(address);
  }

  onFormCancel(): void {
    this.modalRef?.dismiss();
  }

  onFormSave(value: AddressFormSaveValue): void {
    if (this.saving) {
      return;
    }
    this.saving = true;
    this.fieldErrors = [];
    this.cdr.markForCheck();

    const request$ = this.editingAddress
      ? this.accountApi.updateAddress(
          this.editingAddress.id,
          this.toUpdateBody(value),
        )
      : this.accountApi.createAddress(value as CreateAddressRequest);

    request$
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.toast.success(
            this.editingAddress ? 'Adres güncellendi' : 'Adres eklendi',
          );
          this.modalRef?.close();
          this.clearDeepLink();
          this.reloadList();
        },
        error: (err: unknown) => {
          const problem = toProblemDetail(err);
          this.fieldErrors = problem.errors ?? [];
          if (problem.status === 404) {
            this.modalRef?.dismiss();
            this.state = 'notFound';
          }
        },
      });
  }

  onDelete(address: AddressResponse): void {
    if (this.busyId) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Adresi sil',
        message: `"${address.label || address.recipientName}" adresi silinecek. Emin misiniz?`,
        confirmLabel: 'Sil',
        cancelLabel: 'Vazgeç',
        confirmButtonClass: 'btn-danger',
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((ok) => {
        if (!ok) {
          return;
        }
        this.busyId = address.id;
        this.cdr.markForCheck();
        this.accountApi
          .deleteAddress(address.id)
          .pipe(
            takeUntil(this.destroy$),
            finalize(() => {
              this.busyId = null;
              this.cdr.markForCheck();
            }),
          )
          .subscribe({
            next: () => {
              this.toast.success('Adres silindi');
              this.reloadList();
            },
            error: (err: unknown) => {
              const problem = toProblemDetail(err);
              if (problem.status === 404) {
                this.toast.success('Adres silindi');
                this.reloadList();
              }
            },
          });
      });
  }

  onSetDefault(address: AddressResponse): void {
    if (address.isDefault || this.busyId) {
      return;
    }
    this.busyId = address.id;
    this.cdr.markForCheck();
    this.accountApi
      .setDefaultAddress(address.id)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.busyId = null;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.toast.success('Varsayılan adres güncellendi');
          this.reloadList();
        },
        error: () => undefined,
      });
  }

  private openEditModal(address: AddressResponse): void {
    this.editingAddress = address;
    this.fieldErrors = [];
    this.openModal();
  }

  private openModal(): void {
    if (!this.addressModal) {
      return;
    }
    this.modalRef?.dismiss();
    this.modalRef = this.modal.open(this.addressModal, {
      size: 'lg',
      centered: true,
      backdrop: 'static',
    });
    this.modalRef.result.finally(() => {
      this.modalRef = null;
      this.saving = false;
      this.fieldErrors = [];
      if (this.route.snapshot.paramMap.get('id')) {
        this.clearDeepLink();
      }
      this.cdr.markForCheck();
    });
    this.cdr.markForCheck();
  }

  private reloadList(): void {
    this.accountApi
      .listAddresses()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (list) => {
          this.addresses = list;
          this.state = 'ready';
          this.cdr.markForCheck();
        },
        error: () => {
          this.state = 'error';
          this.cdr.markForCheck();
        },
      });
  }

  private clearDeepLink(): void {
    if (!this.route.snapshot.paramMap.get('id')) {
      return;
    }
    void this.router.navigate(['/account/addresses'], { replaceUrl: true });
  }

  private toUpdateBody(value: AddressFormSaveValue): UpdateAddressRequest {
    return {
      recipientName: value.recipientName,
      phone: value.phone,
      line1: value.line1,
      city: value.city,
      country: value.country ?? 'TR',
      isDefault: value.isDefault,
      label: value.label ?? '',
      line2: value.line2 ?? '',
      district: value.district ?? '',
      postalCode: value.postalCode ?? '',
    };
  }
}
