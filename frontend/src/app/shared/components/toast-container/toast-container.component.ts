import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { ToastMessage, ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-toast-container',
  templateUrl: './toast-container.component.html',
  styleUrls: ['./toast-container.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastContainerComponent {
  readonly toasts$: Observable<ToastMessage[]> = this.toastService.toasts$;

  constructor(private readonly toastService: ToastService) {}

  remove(id: number): void {
    this.toastService.remove(id);
  }
}
