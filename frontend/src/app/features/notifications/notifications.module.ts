import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { NotificationsRoutingModule } from './notifications-routing.module';
import { NotificationsPageComponent } from './notifications-page/notifications-page.component';

@NgModule({
  declarations: [NotificationsPageComponent],
  imports: [SharedModule, NotificationsRoutingModule],
})
export class NotificationsModule {}
