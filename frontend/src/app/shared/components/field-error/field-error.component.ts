import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { FieldError } from '../../../core/models';

@Component({
  selector: 'app-field-error',
  templateUrl: './field-error.component.html',
  styleUrls: ['./field-error.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FieldErrorComponent {
  @Input() errors: FieldError[] = [];
  @Input() field: string | null = null;

  get visibleErrors(): FieldError[] {
    if (!this.field) {
      return this.errors;
    }
    return this.errors.filter((e) => e.field === this.field);
  }
}
