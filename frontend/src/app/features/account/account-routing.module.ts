import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ProfilePageComponent } from './profile-page/profile-page.component';
import { AddressesPageComponent } from './addresses-page/addresses-page.component';

const routes: Routes = [
  { path: 'profile', component: ProfilePageComponent },
  { path: 'addresses', component: AddressesPageComponent },
  { path: 'addresses/:id', component: AddressesPageComponent },
  { path: '', pathMatch: 'full', redirectTo: 'profile' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AccountRoutingModule {}
