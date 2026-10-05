import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { UserResponse } from '../../core/models';
import { HeaderComponent } from './header.component';

describe('HeaderComponent', () => {
  let fixture: ComponentFixture<HeaderComponent>;
  let currentUser$: BehaviorSubject<UserResponse | null>;

  beforeEach(async () => {
    currentUser$ = new BehaviorSubject<UserResponse | null>(null);
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, NgbModule],
      declarations: [HeaderComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            currentUser$,
            logout: jasmine.createSpy('logout'),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show guest login/register links', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Kitap Sepeti');
    expect(el.querySelector('a[routerLink="/login"]')).toBeTruthy();
    expect(el.querySelector('a[routerLink="/register"]')).toBeTruthy();
  });

  it('should show user name when logged in', () => {
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
    expect(el.querySelector('a[routerLink="/login"]')).toBeFalsy();
  });
});
