import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { AccountRoutingModule } from './account-routing.module';
import { ProfilePageComponent } from './profile-page/profile-page.component';
import { AddressesPageComponent } from './addresses-page/addresses-page.component';

@NgModule({
  declarations: [ProfilePageComponent, AddressesPageComponent],
  imports: [SharedModule, AccountRoutingModule],
})
export class AccountModule {}
