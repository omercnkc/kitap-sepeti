import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, firstValueFrom } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { UserResponse } from '../models';
import { GuestGuard } from './guest.guard';

describe('GuestGuard', () => {
  let guard: GuestGuard;
  let router: Router;
  let currentUser$: BehaviorSubject<UserResponse | null>;

  beforeEach(() => {
    currentUser$ = new BehaviorSubject<UserResponse | null>(null);
    TestBed.configureTestingModule({
      imports: [RouterTestingModule.withRoutes([])],
      providers: [
        GuestGuard,
        { provide: AuthService, useValue: { currentUser$ } },
      ],
    });
    guard = TestBed.inject(GuestGuard);
    router = TestBed.inject(Router);
  });

  it('allows login/register when guest', async () => {
    const result = await firstValueFrom(
      guard.canActivate({} as never, { url: '/login' } as never),
    );
    expect(result).toBeTrue();
  });

  it('redirects to /books when already logged in', async () => {
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
      guard.canActivate({} as never, { url: '/login' } as never),
    );
    expect(result).toEqual(router.createUrlTree(['/books']));
  });
});
