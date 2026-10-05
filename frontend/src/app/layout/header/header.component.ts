import { ChangeDetectionStrategy, Component, TemplateRef } from '@angular/core';
import { NgbOffcanvas } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { UserResponse } from '../../core/models';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HeaderComponent {
  readonly currentUser$: Observable<UserResponse | null> = this.auth.currentUser$;

  constructor(
    private readonly offcanvas: NgbOffcanvas,
    private readonly auth: AuthService,
  ) {}

  openMenu(content: TemplateRef<unknown>): void {
    this.offcanvas.open(content, {
      position: 'end',
      panelClass: 'app-nav-offcanvas',
    });
  }

  logout(): void {
    this.auth.logout();
  }

  displayName(user: UserResponse): string {
    const name = `${user.firstName} ${user.lastName}`.trim();
    return name || user.email;
  }
}
