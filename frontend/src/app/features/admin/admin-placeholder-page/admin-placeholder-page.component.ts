import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Component({
  selector: 'app-admin-placeholder-page',
  templateUrl: './admin-placeholder-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPlaceholderPageComponent {
  readonly title$: Observable<string> = this.route.data.pipe(
    map((data) => (data['title'] as string) || 'Yönetim'),
  );

  constructor(private readonly route: ActivatedRoute) {}
}
