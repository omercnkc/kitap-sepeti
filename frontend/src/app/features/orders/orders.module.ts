import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { OrdersRoutingModule } from './orders-routing.module';
import { OrderListPageComponent } from './order-list-page/order-list-page.component';
import { OrderDetailPageComponent } from './order-detail-page/order-detail-page.component';

@NgModule({
  declarations: [OrderListPageComponent, OrderDetailPageComponent],
  imports: [SharedModule, OrdersRoutingModule],
})
export class OrdersModule {}
