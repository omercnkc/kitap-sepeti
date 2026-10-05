import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { SharedModule } from '../../../shared/shared.module';
import { LoginPageComponent } from './login-page.component';

describe('LoginPageComponent', () => {
  let fixture: ComponentFixture<LoginPageComponent>;
  let component: LoginPageComponent;
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
      declarations: [LoginPageComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginPageComponent);
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

  it('should login and navigate to /books', () => {
    component.form.setValue({
      email: 'a@b.com',
      password: 'Secret123',
      rememberMe: true,
    });
    component.onSubmit();

    const loginReq = httpMock.expectOne('/api/auth/login');
    loginReq.flush({
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

  it('should show form error on invalid credentials', () => {
    component.form.setValue({
      email: 'a@b.com',
      password: 'wrong',
      rememberMe: false,
    });
    component.onSubmit();

    httpMock.expectOne('/api/auth/login').flush(
      {
        title: 'Unauthorized',
        status: 401,
        code: 'INVALID_CREDENTIALS',
        detail: 'Invalid credentials.',
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(component.formError).toContain('E-posta veya parola');
    expect(component.submitting).toBeFalse();
  });
});
