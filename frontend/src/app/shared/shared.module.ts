import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ToastContainerComponent } from './components/toast-container/toast-container.component';

/**
 * Ortak sunum parçaları (pipe, presentational component) burada export edilir.
 * Servis sağlamaz — lazy feature'larda ayrı instance oluşmasın.
 */
@NgModule({
  declarations: [ToastContainerComponent],
  imports: [CommonModule, RouterModule, NgbModule],
  exports: [CommonModule, RouterModule, NgbModule, ToastContainerComponent],
})
export class SharedModule {}
