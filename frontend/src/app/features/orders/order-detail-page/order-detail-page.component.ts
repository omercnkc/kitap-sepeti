import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-order-detail-page',
  templateUrl: './order-detail-page.component.html',
  styleUrls: ['./order-detail-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderDetailPageComponent {
  readonly orderId: string | null;

  constructor(private readonly route: ActivatedRoute) {
    this.orderId = this.route.snapshot.paramMap.get('id');
  }
}
