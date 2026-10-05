import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, firstValueFrom } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { UserResponse } from '../models';
import { AuthGuard } from './auth.guard';

describe('AuthGuard', () => {
  let guard: AuthGuard;
  let router: Router;
  let currentUser$: BehaviorSubject<UserResponse | null>;

  beforeEach(() => {
    currentUser$ = new BehaviorSubject<UserResponse | null>(null);
    TestBed.configureTestingModule({
      imports: [RouterTestingModule.withRoutes([])],
      providers: [
        AuthGuard,
        { provide: AuthService, useValue: { currentUser$ } },
      ],
    });
    guard = TestBed.inject(AuthGuard);
    router = TestBed.inject(Router);
  });

  it('allows navigation when logged in', async () => {
    currentUser$.next({
      id: 'u1',
      email: 'a@b.com',
      firstName: 'Ali',
      lastName: 'Veli',
      phone: null,
      role: 'USER',
      status: 'ACTIVE',
    });

    const result = await firstValueFrom(
      guard.canActivate({} as never, { url: '/cart' } as never),
    );
    expect(result).toBeTrue();
  });

  it('redirects to login with returnUrl when guest', async () => {
    const result = await firstValueFrom(
      guard.canActivate({} as never, { url: '/cart' } as never),
    );
    expect(result).toEqual(
      router.createUrlTree(['/login'], { queryParams: { returnUrl: '/cart' } }),
    );
  });
});
