import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-addresses-page',
  templateUrl: './addresses-page.component.html',
  styleUrls: ['./addresses-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddressesPageComponent {}
