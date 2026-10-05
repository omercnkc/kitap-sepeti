import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { AdminPlaceholderPageComponent } from './admin-placeholder-page/admin-placeholder-page.component';
import { AdminRoutingModule } from './admin-routing.module';
import { AdminShellComponent } from './admin-shell/admin-shell.component';

@NgModule({
  declarations: [AdminShellComponent, AdminPlaceholderPageComponent],
  imports: [SharedModule, AdminRoutingModule],
})
export class AdminModule {}
