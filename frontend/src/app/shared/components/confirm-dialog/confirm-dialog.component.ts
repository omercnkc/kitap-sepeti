import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

export interface ConfirmDialogOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  confirmButtonClass?: string;
}

@Component({
  selector: 'app-confirm-dialog',
  templateUrl: './confirm-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent {
  @Input() title = 'Onay';
  @Input() message = '';
  @Input() confirmLabel = 'Onayla';
  @Input() cancelLabel = 'Vazgeç';
  @Input() confirmButtonClass = 'btn-danger';

  constructor(readonly activeModal: NgbActiveModal) {}
}
