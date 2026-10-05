import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { AccountApi } from '../../../core/api/account.api';
import { AddressResponse } from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { SharedModule } from '../../../shared/shared.module';
import { AddressesPageComponent } from './addresses-page.component';

describe('AddressesPageComponent', () => {
  let fixture: ComponentFixture<AddressesPageComponent>;
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let listSpy: jasmine.Spy;
  let getSpy: jasmine.Spy;
  let createSpy: jasmine.Spy;
  let updateSpy: jasmine.Spy;
  let deleteSpy: jasmine.Spy;
  let setDefaultSpy: jasmine.Spy;
  let confirmSpy: jasmine.Spy;
  let toastSuccessSpy: jasmine.Spy;
  let modalOpenSpy: jasmine.Spy;
  let router: Router;

  const address: AddressResponse = {
    id: 'a1',
    label: 'Ev',
    recipientName: 'Ali Veli',
    phone: '5551112233',
    line1: 'Cadde 1',
    line2: null,
    district: 'Kadıköy',
    city: 'İstanbul',
    postalCode: '34710',
    country: 'TR',
    isDefault: true,
    createdAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(async () => {
    paramMap$ = new BehaviorSubject(convertToParamMap({}));
    listSpy = jasmine.createSpy('listAddresses').and.returnValue(of([address]));
    getSpy = jasmine.createSpy('getAddress').and.returnValue(of(address));
    createSpy = jasmine.createSpy('createAddress').and.returnValue(of(address));
    updateSpy = jasmine.createSpy('updateAddress').and.returnValue(of(address));
    deleteSpy = jasmine.createSpy('deleteAddress').and.returnValue(of(void 0));
    setDefaultSpy = jasmine.createSpy('setDefaultAddress').and.returnValue(of(address));
    confirmSpy = jasmine.createSpy('confirm').and.returnValue(of(true));
    toastSuccessSpy = jasmine.createSpy('success');
    modalOpenSpy = jasmine.createSpy('open').and.returnValue({
      result: Promise.resolve(),
      close: jasmine.createSpy('close'),
      dismiss: jasmine.createSpy('dismiss'),
    });

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, SharedModule],
      declarations: [AddressesPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: paramMap$.asObservable(),
            snapshot: { paramMap: convertToParamMap({}) },
          },
        },
        {
          provide: AccountApi,
          useValue: {
            listAddresses: listSpy,
            getAddress: getSpy,
            createAddress: createSpy,
            updateAddress: updateSpy,
            deleteAddress: deleteSpy,
            setDefaultAddress: setDefaultSpy,
          },
        },
        { provide: ConfirmDialogService, useValue: { confirm: confirmSpy } },
        { provide: ToastService, useValue: { success: toastSuccessSpy } },
        { provide: NgbModal, useValue: { open: modalOpenSpy } },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(AddressesPageComponent);
    fixture.detectChanges();
  });

  it('lists addresses with default badge', () => {
    expect(listSpy).toHaveBeenCalled();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Ev');
    expect(el.textContent).toContain('Varsayılan');
    expect(el.textContent).toContain('Cadde 1');
  });

  it('shows empty state when no addresses', () => {
    listSpy.and.returnValue(of([]));
    paramMap$.next(convertToParamMap({}));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Henüz adres yok');
  });

  it('shows not found for missing deep-link id', () => {
    getSpy.and.returnValue(
      throwError(() => ({
        title: 'Not Found',
        status: 404,
        code: 'RESOURCE_NOT_FOUND',
      })),
    );
    const route = TestBed.inject(ActivatedRoute) as unknown as {
      snapshot: { paramMap: ReturnType<typeof convertToParamMap> };
    };
    route.snapshot.paramMap = convertToParamMap({ id: 'missing' });
    paramMap$.next(convertToParamMap({ id: 'missing' }));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Adres bulunamadı');
  });

  it('creates address from modal save and reloads list', () => {
    fixture.componentInstance.openCreate();
    expect(modalOpenSpy).toHaveBeenCalled();

    listSpy.calls.reset();
    listSpy.and.returnValue(of([address]));
    fixture.componentInstance.onFormSave({
      recipientName: 'Ali Veli',
      phone: '555',
      line1: 'Cadde 1',
      city: 'İstanbul',
      country: 'TR',
      isDefault: true,
    });

    expect(createSpy).toHaveBeenCalled();
    expect(toastSuccessSpy).toHaveBeenCalledWith('Adres eklendi');
    expect(listSpy).toHaveBeenCalled();
  });

  it('deletes after confirm and reloads', () => {
    listSpy.calls.reset();
    listSpy.and.returnValue(of([]));
    fixture.componentInstance.onDelete(address);
    expect(confirmSpy).toHaveBeenCalled();
    expect(deleteSpy).toHaveBeenCalledWith('a1');
    expect(toastSuccessSpy).toHaveBeenCalledWith('Adres silindi');
    expect(listSpy).toHaveBeenCalled();
  });

  it('sets default and reloads', () => {
    const other = { ...address, id: 'a2', isDefault: false, label: 'İş' };
    listSpy.and.returnValue(of([address, other]));
    paramMap$.next(convertToParamMap({}));
    fixture.detectChanges();

    listSpy.calls.reset();
    listSpy.and.returnValue(of([{ ...other, isDefault: true }, { ...address, isDefault: false }]));
    fixture.componentInstance.onSetDefault(other);
    expect(setDefaultSpy).toHaveBeenCalledWith('a2');
    expect(toastSuccessSpy).toHaveBeenCalledWith('Varsayılan adres güncellendi');
    expect(listSpy).toHaveBeenCalled();
  });

  it('opens edit modal for deep-link id', fakeAsync(() => {
    const route = TestBed.inject(ActivatedRoute) as unknown as {
      snapshot: { paramMap: ReturnType<typeof convertToParamMap> };
    };
    route.snapshot.paramMap = convertToParamMap({ id: 'a1' });
    paramMap$.next(convertToParamMap({ id: 'a1' }));
    fixture.detectChanges();
    tick();
    expect(getSpy).toHaveBeenCalledWith('a1');
    expect(modalOpenSpy).toHaveBeenCalled();
  }));
});
