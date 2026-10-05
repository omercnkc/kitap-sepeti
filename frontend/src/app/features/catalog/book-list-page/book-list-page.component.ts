import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-book-list-page',
  templateUrl: './book-list-page.component.html',
  styleUrls: ['./book-list-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookListPageComponent {
  constructor(private readonly toastService: ToastService) {}

  showSuccessToast(): void {
    this.toastService.success('İşlem başarılı (test)');
  }

  showErrorToast(): void {
    this.toastService.error('Bir hata oluştu (test)');
  }
}
