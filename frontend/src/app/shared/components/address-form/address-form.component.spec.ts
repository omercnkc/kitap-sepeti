import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { AddressResponse } from '../../../core/models';
import { FieldErrorComponent } from '../field-error/field-error.component';
import { AddressFormComponent } from './address-form.component';

describe('AddressFormComponent', () => {
  let fixture: ComponentFixture<AddressFormComponent>;
  let component: AddressFormComponent;

  const address: AddressResponse = {
    id: 'a1',
    label: 'Ev',
    recipientName: 'Ali Veli',
    phone: '5551112233',
    line1: 'Cadde 1',
    line2: 'Daire 2',
    district: 'Kadıköy',
    city: 'İstanbul',
    postalCode: '34710',
    country: 'TR',
    isDefault: true,
    createdAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
      declarations: [AddressFormComponent, FieldErrorComponent],
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

  it('patches from address input and emits save payload', () => {
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
    fixture.detectChanges();

    expect(component.canSave).toBeTrue();
    component.onSubmit();
    expect(saveSpy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        recipientName: 'Ali Veli',
        phone: '5551112233',
        line1: 'Cadde 1',
        city: 'İstanbul',
        country: 'TR',
        isDefault: true,
        label: 'Ev',
      }),
    );
  });

  it('emits cancel', () => {
    const cancelSpy = jasmine.createSpy('cancel');
    component.cancel.subscribe(cancelSpy);
    component.onCancel();
    expect(cancelSpy).toHaveBeenCalled();
  });
});
