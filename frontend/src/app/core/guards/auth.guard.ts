import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Observable } from 'rxjs';
import { map, take } from 'rxjs/operators';
import { AuthService } from '../auth/auth.service';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  canActivate(
    _route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot,
  ): Observable<boolean | UrlTree> {
    return this.auth.currentUser$.pipe(
      take(1),
      map((user) => {
        if (user) {
          return true;
        }
        return this.router.createUrlTree(['/login'], {
          queryParams: { returnUrl: state.url },
        });
      }),
    );
  }
}
