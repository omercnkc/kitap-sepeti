import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { SharedModule } from '../../../shared/shared.module';
import { RegisterPageComponent } from './register-page.component';

describe('RegisterPageComponent', () => {
  let fixture: ComponentFixture<RegisterPageComponent>;
  let component: RegisterPageComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ReactiveFormsModule,
        HttpClientTestingModule,
        RouterTestingModule.withRoutes([{ path: 'books', children: [] }]),
        SharedModule,
      ],
      declarations: [RegisterPageComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create with invalid empty form', () => {
    expect(component).toBeTruthy();
    expect(component.form.valid).toBeFalse();
  });

  it('should reject short password', () => {
    component.form.setValue({
      firstName: 'Ali',
      lastName: 'Veli',
      email: 'a@b.com',
      password: 'short',
      phone: '',
      rememberMe: false,
    });
    expect(component.form.get('password')!.hasError('minlength')).toBeTrue();
    component.onSubmit();
    httpMock.expectNone(() => true);
  });

  it('should register without phone when blank and navigate to /books', () => {
    component.form.setValue({
      firstName: 'Ali',
      lastName: 'Veli',
      email: 'a@b.com',
      password: 'Secret123',
      phone: '  ',
      rememberMe: false,
    });
    component.onSubmit();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({
      firstName: 'Ali',
      lastName: 'Veli',
      email: 'a@b.com',
      password: 'Secret123',
    });
    expect(req.request.body.phone).toBeUndefined();
    req.flush({
      accessToken: 'access',
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
    });

    httpMock.expectOne('/api/me').flush({
      id: 'u1',
      email: 'a@b.com',
      firstName: 'Ali',
      lastName: 'Veli',
      phone: null,
      role: 'USER',
      status: 'ACTIVE',
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/books');
  });

  it('should include phone when provided', () => {
    component.form.setValue({
      firstName: 'Ali',
      lastName: 'Veli',
      email: 'a@b.com',
      password: 'Secret123',
      phone: '5551112233',
      rememberMe: true,
    });
    component.onSubmit();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.body.phone).toBe('5551112233');
    req.flush({
      accessToken: 'access',
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
    });
    httpMock.expectOne('/api/me').flush({
      id: 'u1',
      email: 'a@b.com',
      firstName: 'Ali',
      lastName: 'Veli',
      phone: '5551112233',
      role: 'USER',
      status: 'ACTIVE',
    });
  });

  it('should show error on EMAIL_ALREADY_EXISTS', () => {
    component.form.setValue({
      firstName: 'Ali',
      lastName: 'Veli',
      email: 'a@b.com',
      password: 'Secret123',
      phone: '',
      rememberMe: false,
    });
    component.onSubmit();

    httpMock.expectOne('/api/auth/register').flush(
      {
        title: 'Conflict',
        status: 409,
        code: 'EMAIL_ALREADY_EXISTS',
        detail: 'Email is already registered.',
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(component.formError).toContain('e-posta');
    expect(component.submitting).toBeFalse();
  });
});
