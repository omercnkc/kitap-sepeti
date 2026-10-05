import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { CartRoutingModule } from './cart-routing.module';
import { CartPageComponent } from './cart-page/cart-page.component';

@NgModule({
  declarations: [CartPageComponent],
  imports: [SharedModule, CartRoutingModule],
})
export class CartModule {}
