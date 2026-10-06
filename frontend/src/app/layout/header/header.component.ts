import { ChangeDetectionStrategy, Component, TemplateRef } from '@angular/core';
import { Router } from '@angular/router';
import { NgbOffcanvas } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthService } from '../../core/auth/auth.service';
import { CartStore } from '../../core/cart/cart.store';
import { UserResponse } from '../../core/models';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HeaderComponent {
  readonly currentUser$: Observable<UserResponse | null> = this.auth.currentUser$;
  readonly cartCount$: Observable<number> = this.cartStore.count$;
  /** Kural A: ADMIN iken Sepet her yerde gizli; USER/misafirde görünür. */
  readonly showCart$: Observable<boolean> = this.currentUser$.pipe(
    map((user) => !(user && this.auth.isAdmin(user))),
  );

  constructor(
    private readonly offcanvas: NgbOffcanvas,
    private readonly auth: AuthService,
    private readonly cartStore: CartStore,
    private readonly router: Router,
  ) {}

  openMenu(content: TemplateRef<unknown>): void {
    this.offcanvas.open(content, {
      position: 'end',
      panelClass: 'app-nav-offcanvas',
    });
  }

  logout(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/books');
  }

  isAdmin(user: UserResponse): boolean {
    return this.auth.isAdmin(user);
  }

  /** Header /books: ADMIN → Vitrin, USER/misafir → Kitaplar. */
  booksNavLabel(user: UserResponse | null): string {
    return user != null && this.isAdmin(user) ? 'Vitrin' : 'Kitaplar';
  }

  displayName(user: UserResponse): string {
    const name = `${user.firstName} ${user.lastName}`.trim();
    return name || user.email;
  }
}
