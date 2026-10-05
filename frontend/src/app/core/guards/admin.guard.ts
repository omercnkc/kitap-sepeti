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

@Injectable({ providedIn: 'root' })
export class AdminGuard implements CanActivate, CanLoad {
  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  canActivate(
    _route: ActivatedRouteSnapshot,
    _state: RouterStateSnapshot,
  ): Observable<boolean | UrlTree> {
    return this.checkAdmin();
  }

  canLoad(_route: Route, _segments: UrlSegment[]): Observable<boolean | UrlTree> {
    return this.checkAdmin();
  }

  private checkAdmin(): Observable<boolean | UrlTree> {
    return this.auth.currentUser$.pipe(
      take(1),
      map((user) => {
        if (user && this.auth.isAdmin(user)) {
          return true;
        }
        return this.router.createUrlTree(['/books']);
      }),
    );
  }
}
