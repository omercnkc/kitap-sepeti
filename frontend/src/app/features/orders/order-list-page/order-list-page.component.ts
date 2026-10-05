import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-order-list-page',
  templateUrl: './order-list-page.component.html',
  styleUrls: ['./order-list-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderListPageComponent {}
