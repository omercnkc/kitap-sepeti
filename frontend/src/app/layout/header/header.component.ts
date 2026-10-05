import { ChangeDetectionStrategy, Component, TemplateRef } from '@angular/core';
import { NgbOffcanvas } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HeaderComponent {
  constructor(private readonly offcanvas: NgbOffcanvas) {}

  openMenu(content: TemplateRef<unknown>): void {
    this.offcanvas.open(content, {
      position: 'end',
      panelClass: 'app-nav-offcanvas',
    });
  }
}
