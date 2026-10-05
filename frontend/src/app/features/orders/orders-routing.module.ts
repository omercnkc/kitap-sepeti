import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { OrderListPageComponent } from './order-list-page/order-list-page.component';
import { OrderDetailPageComponent } from './order-detail-page/order-detail-page.component';

const routes: Routes = [
  { path: '', component: OrderListPageComponent },
  { path: ':id', component: OrderDetailPageComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class OrdersRoutingModule {}
