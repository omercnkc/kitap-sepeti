import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { TrAddressDataService } from '../../../core/geo/tr-address-data.service';
import { TrIl, TrIlce, TrMahalle } from '../../../core/geo/tr-address.models';
import { AddressResponse } from '../../../core/models';
import { FieldErrorComponent } from '../field-error/field-error.component';
import { AddressFormComponent } from './address-form.component';

describe('AddressFormComponent', () => {
  let fixture: ComponentFixture<AddressFormComponent>;
  let component: AddressFormComponent;

  const iller: TrIl[] = [
    { sehir_id: '34', sehir_adi: 'İSTANBUL' },
    { sehir_id: '6', sehir_adi: 'ANKARA' },
  ];

  const ilceler34: TrIlce[] = [
    { ilce_id: '1103', ilce_adi: 'KADIKÖY', sehir_id: '34', sehir_adi: 'İSTANBUL' },
    { ilce_id: '1166', ilce_adi: 'BEŞİKTAŞ', sehir_id: '34', sehir_adi: 'İSTANBUL' },
  ];

  const mahalleler1103: TrMahalle[] = [
    {
      mahalle_id: '1',
      mahalle_adi: 'CAFERAĞA MAHALLESİ',
      ilce_id: '1103',
      ilce_adi: 'KADIKÖY',
      sehir_id: '34',
      sehir_adi: 'İSTANBUL',
    },
  ];

  const address: AddressResponse = {
    id: 'a1',
    label: 'Ev',
    recipientName: 'Ali Veli',
    phone: '5551112233',
    line1: 'CAFERAĞA MAHALLESİ',
    line2: 'Daire 2',
    district: 'KADIKÖY',
    city: 'İSTANBUL',
    postalCode: '34710',
    country: 'TR',
    isDefault: true,
    createdAt: '2026-01-01T00:00:00Z',
  };

  const geoStub = {
    getIller: jasmine.createSpy('getIller').and.returnValue(of(iller)),
    getIlceler: jasmine.createSpy('getIlceler').and.callFake((sehirId: string) =>
      of(sehirId === '34' ? ilceler34 : []),
    ),
    getMahalleler: jasmine.createSpy('getMahalleler').and.callFake((ilceId: string) =>
      of(ilceId === '1103' ? mahalleler1103 : []),
    ),
  };

  beforeEach(async () => {
    geoStub.getIller.calls.reset();
    geoStub.getIlceler.calls.reset();
    geoStub.getMahalleler.calls.reset();
    geoStub.getIller.and.returnValue(of(iller));
    geoStub.getIlceler.and.callFake((sehirId: string) =>
      of(sehirId === '34' ? ilceler34 : []),
    );
    geoStub.getMahalleler.and.callFake((ilceId: string) =>
      of(ilceId === '1103' ? mahalleler1103 : []),
    );

    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
      declarations: [AddressFormComponent, FieldErrorComponent],
      providers: [{ provide: TrAddressDataService, useValue: geoStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(AddressFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('disables save when required fields are empty', () => {
    expect(component.canSave).toBeFalse();
    const btn = (fixture.nativeElement as HTMLElement).querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
  });

  it('resets ilce/mahalle when il changes and emits mapped save payload', fakeAsync(() => {
    const saveSpy = jasmine.createSpy('save');
    component.save.subscribe(saveSpy);

    component.form.patchValue({
      recipientName: 'Ali Veli',
      phone: '5551112233',
      sehirId: '34',
    });
    tick();
    fixture.detectChanges();

    expect(geoStub.getIlceler).toHaveBeenCalledWith('34');
    expect(component.form.get('ilceId')!.value).toBe('');
    expect(component.form.get('mahalleId')!.value).toBe('');
    expect(component.form.get('city')!.value).toBe('İSTANBUL');

    component.form.patchValue({ ilceId: '1103' });
    tick();
    fixture.detectChanges();
    expect(geoStub.getMahalleler).toHaveBeenCalledWith('1103');
    expect(component.form.get('district')!.value).toBe('KADIKÖY');
    expect(component.form.get('mahalleId')!.value).toBe('');

    component.form.patchValue({
      mahalleId: '1',
      line2: 'Moda Cad. No:1',
      postalCode: '34710',
      isDefault: true,
      label: 'Ev',
    });
    tick();
    fixture.detectChanges();

    expect(component.form.get('line1')!.value).toBe('CAFERAĞA MAHALLESİ');
    expect(component.canSave).toBeTrue();
    component.onSubmit();
    expect(saveSpy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        recipientName: 'Ali Veli',
        phone: '5551112233',
        line1: 'CAFERAĞA MAHALLESİ',
        line2: 'Moda Cad. No:1',
        district: 'KADIKÖY',
        city: 'İSTANBUL',
        country: 'TR',
        isDefault: true,
        label: 'Ev',
      }),
    );
  }));

  it('patches from address input via geo match and emits save payload', fakeAsync(() => {
    const saveSpy = jasmine.createSpy('save');
    component.save.subscribe(saveSpy);

    component.address = address;
    component.ngOnChanges({
      address: {
        currentValue: address,
        previousValue: null,
        firstChange: true,
        isFirstChange: () => true,
      },
    });
    tick();
    fixture.detectChanges();

    expect(component.form.get('sehirId')!.value).toBe('34');
    expect(component.form.get('ilceId')!.value).toBe('1103');
    expect(component.form.get('mahalleId')!.value).toBe('1');
    expect(component.canSave).toBeTrue();
    component.onSubmit();
    expect(saveSpy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        recipientName: 'Ali Veli',
        phone: '5551112233',
        line1: 'CAFERAĞA MAHALLESİ',
        city: 'İSTANBUL',
        district: 'KADIKÖY',
        country: 'TR',
        isDefault: true,
        label: 'Ev',
      }),
    );
  }));

  it('emits cancel', () => {
    const cancelSpy = jasmine.createSpy('cancel');
    component.cancel.subscribe(cancelSpy);
    component.onCancel();
    expect(cancelSpy).toHaveBeenCalled();
  });
});
