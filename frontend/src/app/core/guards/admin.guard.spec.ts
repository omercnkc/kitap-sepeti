import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, firstValueFrom } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { UserResponse } from '../models';
import { AdminGuard } from './admin.guard';

describe('AdminGuard', () => {
  let guard: AdminGuard;
  let router: Router;
  let currentUser$: BehaviorSubject<UserResponse | null>;
  let isAdmin: jasmine.Spy;

  const user = (role: 'USER' | 'ADMIN'): UserResponse => ({
    id: 'u1',
    email: 'a@b.com',
    firstName: 'Ali',
    lastName: 'Veli',
    phone: null,
    role,
    status: 'ACTIVE',
  });

  beforeEach(() => {
    currentUser$ = new BehaviorSubject<UserResponse | null>(null);
    isAdmin = jasmine.createSpy('isAdmin').and.returnValue(false);
    TestBed.configureTestingModule({
      imports: [RouterTestingModule.withRoutes([])],
      providers: [
        AdminGuard,
        {
          provide: AuthService,
          useValue: { currentUser$, isAdmin },
        },
      ],
    });
    guard = TestBed.inject(AdminGuard);
    router = TestBed.inject(Router);
  });

  it('allows ADMIN role', async () => {
    const admin = user('ADMIN');
    currentUser$.next(admin);
    isAdmin.and.returnValue(true);

    const result = await firstValueFrom(
      guard.canActivate({} as never, { url: '/admin' } as never),
    );
    expect(result).toBeTrue();
    expect(isAdmin).toHaveBeenCalledWith(admin);
  });

  it('redirects USER to /books', async () => {
    currentUser$.next(user('USER'));
    isAdmin.and.returnValue(false);

    const result = await firstValueFrom(
      guard.canActivate({} as never, { url: '/admin' } as never),
    );
    expect(result).toEqual(router.createUrlTree(['/books']));
  });

  it('canLoad redirects non-admin to /books', async () => {
    currentUser$.next(null);
    const result = await firstValueFrom(guard.canLoad({} as never, []));
    expect(result).toEqual(router.createUrlTree(['/books']));
  });
});
