import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  CanLoad,
  Route,
  Router,
  RouterStateSnapshot,
  UrlSegment,
  UrlTree,
} from '@angular/router';
import { Observable } from 'rxjs';
import { map, take } from 'rxjs/operators';
import { AuthService } from '../auth/auth.service';

/**
 * Müşteri akışı (checkout/siparişler): ADMIN engellenir → /admin/books.
 * AuthGuard ile birlikte kullanılır (önce giriş kontrolü).
 */
@Injectable({ providedIn: 'root' })
export class NonAdminGuard implements CanActivate, CanLoad {
  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  canActivate(
    _route: ActivatedRouteSnapshot,
    _state: RouterStateSnapshot,
  ): Observable<boolean | UrlTree> {
    return this.checkNonAdmin();
  }

  canLoad(_route: Route, _segments: UrlSegment[]): Observable<boolean | UrlTree> {
    return this.checkNonAdmin();
  }

  private checkNonAdmin(): Observable<boolean | UrlTree> {
    return this.auth.currentUser$.pipe(
      take(1),
      map((user) => {
        if (user && this.auth.isAdmin(user)) {
          return this.router.createUrlTree(['/admin', 'books']);
        }
        return true;
      }),
    );
  }
}
