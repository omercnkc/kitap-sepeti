import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartStore } from '../../core/cart/cart.store';
import { UserResponse } from '../../core/models';
import { HeaderComponent } from './header.component';

describe('HeaderComponent', () => {
  let fixture: ComponentFixture<HeaderComponent>;
  let currentUser$: BehaviorSubject<UserResponse | null>;
  let cartCount$: BehaviorSubject<number>;
  let logoutSpy: jasmine.Spy;
  let isAdminSpy: jasmine.Spy;
  let router: Router;

  beforeEach(async () => {
    currentUser$ = new BehaviorSubject<UserResponse | null>(null);
    cartCount$ = new BehaviorSubject<number>(0);
    logoutSpy = jasmine.createSpy('logout');
    isAdminSpy = jasmine.createSpy('isAdmin').and.returnValue(false);
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, NgbModule],
      declarations: [HeaderComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            currentUser$,
            logout: logoutSpy,
            isAdmin: isAdminSpy,
          },
        },
        {
          provide: CartStore,
          useValue: { count$: cartCount$.asObservable() },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show logo brand link and guest login/register', () => {
    const el = fixture.nativeElement as HTMLElement;
    const logo = el.querySelector(
      'a.navbar-brand[routerLink="/books"] img.app-header__logo',
    ) as HTMLImageElement | null;
    expect(logo).toBeTruthy();
    expect(logo?.getAttribute('src')).toBe('assets/brand/logo.png');
    expect(logo?.getAttribute('alt')).toBe('Kitap Sepeti');
    expect(el.querySelector('a[routerLink="/login"]')).toBeTruthy();
    expect(el.querySelector('a[routerLink="/register"]')).toBeTruthy();
  });

  it('should show account menu when logged in', () => {
    currentUser$.next({
      id: 'u1',
      email: 'a@b.com',
      firstName: 'Ali',
      lastName: 'Veli',
      phone: null,
      role: 'USER',
      status: 'ACTIVE',
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Ali Veli');
    expect(el.querySelector('a[routerLink="/account/profile"]')).toBeTruthy();
    expect(el.querySelector('a[routerLink="/account/addresses"]')).toBeTruthy();
    expect(el.querySelector('a[routerLink="/orders"]')).toBeTruthy();
    expect(el.querySelector('a[routerLink="/admin"]')).toBeFalsy();
    expect(el.querySelector('a[routerLink="/login"]')).toBeFalsy();
  });

  it('should show cart badge when count > 0', () => {
    cartCount$.next(3);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const badge = el.querySelector(
      'a[routerLink="/cart"] .header-nav-btn__badge',
    );
    expect(badge?.textContent?.trim()).toBe('3');
  });

  it('should hide cart for ADMIN (rule A)', () => {
    isAdminSpy.and.returnValue(true);
    cartCount$.next(3);
    currentUser$.next({
      id: 'u1',
      email: 'admin@b.com',
      firstName: 'Ada',
      lastName: 'Min',
      phone: null,
      role: 'ADMIN',
      status: 'ACTIVE',
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('a[routerLink="/cart"]')).toBeFalsy();
    expect(el.querySelector('a[routerLink="/admin"]')).toBeTruthy();
  });

  it('should show cart for USER', () => {
    isAdminSpy.and.returnValue(false);
    currentUser$.next({
      id: 'u1',
      email: 'a@b.com',
      firstName: 'Ali',
      lastName: 'Veli',
      phone: null,
      role: 'USER',
      status: 'ACTIVE',
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('a[routerLink="/cart"]')).toBeTruthy();
  });

  it('should show admin link for ADMIN', () => {
    isAdminSpy.and.returnValue(true);
    currentUser$.next({
      id: 'u1',
      email: 'admin@b.com',
      firstName: 'Ada',
      lastName: 'Min',
      phone: null,
      role: 'ADMIN',
      status: 'ACTIVE',
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('a[routerLink="/admin"]')).toBeTruthy();
  });

  it('logout clears session and navigates home', () => {
    fixture.componentInstance.logout();
    expect(logoutSpy).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/books');
  });
});
