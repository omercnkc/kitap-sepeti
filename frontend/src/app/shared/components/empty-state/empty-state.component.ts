import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  templateUrl: './empty-state.component.html',
  styleUrls: ['./empty-state.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  @Input() title = 'Henüz içerik yok';
  @Input() message = '';
  @Input() ctaLabel: string | null = null;
  @Input() ctaLink: string | null = null;
}
