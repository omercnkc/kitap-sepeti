import { ChangeDetectionStrategy, Component, TemplateRef } from '@angular/core';
import { NgbOffcanvas } from '@ng-bootstrap/ng-bootstrap';

export interface AdminNavItem {
  path: string;
  label: string;
}

@Component({
  selector: 'app-admin-shell',
  templateUrl: './admin-shell.component.html',
  styleUrls: ['./admin-shell.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminShellComponent {
  readonly navItems: AdminNavItem[] = [
    { path: 'authors', label: 'Yazarlar' },
    { path: 'categories', label: 'Kategoriler' },
    { path: 'books', label: 'Kitaplar' },
  ];

  constructor(readonly offcanvas: NgbOffcanvas) {}

  openNav(content: TemplateRef<unknown>): void {
    this.offcanvas.open(content, {
      position: 'start',
      panelClass: 'admin-nav-offcanvas',
    });
  }
}
