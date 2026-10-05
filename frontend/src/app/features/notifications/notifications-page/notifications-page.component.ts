import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-notifications-page',
  templateUrl: './notifications-page.component.html',
  styleUrls: ['./notifications-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationsPageComponent {}
