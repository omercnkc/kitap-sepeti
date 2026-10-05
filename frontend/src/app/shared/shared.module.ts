import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Ortak sunum parçaları (pipe, presentational component) burada export edilir.
 * Servis sağlamaz — lazy feature'larda ayrı instance oluşmasın.
 */
@NgModule({
  imports: [CommonModule],
  exports: [CommonModule],
})
export class SharedModule {}
