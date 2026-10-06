import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, firstValueFrom } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { UserResponse } from '../models';
import { NonAdminGuard } from './non-admin.guard';

describe('NonAdminGuard', () => {
  let guard: NonAdminGuard;
  let router: Router;
  let currentUser$: BehaviorSubject<UserResponse | null>;
  let isAdmin: jasmine.Spy;

  const user = (role: 'USER' | 'ADMIN'): UserResponse => ({
    id: 'u1',
    email: 'a@b.com',
    firstName: 'A',
    lastName: 'B',
    phone: null,
    role,
    status: 'ACTIVE',
  });

  beforeEach(() => {
    currentUser$ = new BehaviorSubject<UserResponse | null>(null);
    isAdmin = jasmine.createSpy('isAdmin').and.returnValue(false);
    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [
        NonAdminGuard,
        {
          provide: AuthService,
          useValue: { currentUser$, isAdmin },
        },
      ],
    });
    guard = TestBed.inject(NonAdminGuard);
    router = TestBed.inject(Router);
  });

  it('allows USER', async () => {
    const u = user('USER');
    currentUser$.next(u);
    isAdmin.and.returnValue(false);
    const result = await firstValueFrom(
      guard.canActivate({} as never, { url: '/orders' } as never),
    );
    expect(result).toBe(true);
    expect(isAdmin).toHaveBeenCalledWith(u);
  });

  it('redirects ADMIN to /admin/books', async () => {
    const admin = user('ADMIN');
    currentUser$.next(admin);
    isAdmin.and.returnValue(true);
    const result = await firstValueFrom(
      guard.canActivate({} as never, { url: '/checkout' } as never),
    );
    expect(result).toEqual(router.createUrlTree(['/admin', 'books']));
  });

  it('allows guest (AuthGuard handles login)', async () => {
    currentUser$.next(null);
    const result = await firstValueFrom(
      guard.canActivate({} as never, { url: '/orders' } as never),
    );
    expect(result).toBe(true);
  });
});
