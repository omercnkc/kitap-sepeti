import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { UserResponse } from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { SharedModule } from '../../../shared/shared.module';
import { ProfilePageComponent } from './profile-page.component';

describe('ProfilePageComponent', () => {
  let fixture: ComponentFixture<ProfilePageComponent>;
  let getMeSpy: jasmine.Spy;
  let updateProfileSpy: jasmine.Spy;
  let toastSuccessSpy: jasmine.Spy;

  const user: UserResponse = {
    id: 'u1',
    email: 'a@b.com',
    firstName: 'Ali',
    lastName: 'Veli',
    phone: '5551112233',
    role: 'USER',
    status: 'ACTIVE',
  };

  beforeEach(async () => {
    getMeSpy = jasmine.createSpy('getMe').and.returnValue(of(user));
    updateProfileSpy = jasmine.createSpy('updateProfile').and.returnValue(
      of({ ...user, phone: null }),
    );
    toastSuccessSpy = jasmine.createSpy('success');

    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, RouterTestingModule, SharedModule],
      declarations: [ProfilePageComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            getMe: getMeSpy,
            updateProfile: updateProfileSpy,
          },
        },
        {
          provide: ToastService,
          useValue: { success: toastSuccessSpy },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfilePageComponent);
    fixture.detectChanges();
  });

  it('loads profile into the form', () => {
    expect(getMeSpy).toHaveBeenCalled();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('a@b.com');
    expect(fixture.componentInstance.form.value).toEqual({
      firstName: 'Ali',
      lastName: 'Veli',
      phone: '5551112233',
    });
  });

  it('saves profile and clears phone with empty string', () => {
    fixture.detectChanges();
    fixture.componentInstance.form.patchValue({ phone: '  ' });
    fixture.componentInstance.onSubmit();
    fixture.detectChanges();

    expect(updateProfileSpy).toHaveBeenCalledWith({
      firstName: 'Ali',
      lastName: 'Veli',
      phone: '',
    });
    expect(toastSuccessSpy).toHaveBeenCalledWith('Profil güncellendi');
    expect(fixture.componentInstance.form.value.phone).toBe('');
  });

  it('maps field errors from problem detail', () => {
    fixture.detectChanges();
    updateProfileSpy.and.returnValue(
      throwError(() => ({
        title: 'Bad Request',
        status: 400,
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'firstName', message: 'must not be blank' }],
      })),
    );
    fixture.componentInstance.onSubmit();
    fixture.detectChanges();
    expect(fixture.componentInstance.fieldErrors.length).toBe(1);
    expect(fixture.componentInstance.formError).toContain('kontrol edin');
  });
});
