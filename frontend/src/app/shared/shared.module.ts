import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ToastContainerComponent } from './components/toast-container/toast-container.component';
import { SpinnerComponent } from './components/spinner/spinner.component';
import { EmptyStateComponent } from './components/empty-state/empty-state.component';
import { FieldErrorComponent } from './components/field-error/field-error.component';
import { BookCardComponent } from './components/book-card/book-card.component';
import { PaginationComponent } from './components/pagination/pagination.component';
import { ConfirmDialogComponent } from './components/confirm-dialog/confirm-dialog.component';

/**
 * Ortak sunum parçaları (pipe, presentational component) burada export edilir.
 * Servis sağlamaz — lazy feature'larda ayrı instance oluşmasın.
 */
@NgModule({
  declarations: [
    ToastContainerComponent,
    SpinnerComponent,
    EmptyStateComponent,
    FieldErrorComponent,
    BookCardComponent,
    PaginationComponent,
    ConfirmDialogComponent,
  ],
  imports: [CommonModule, RouterModule, NgbModule],
  exports: [
    CommonModule,
    RouterModule,
    NgbModule,
    ToastContainerComponent,
    SpinnerComponent,
    EmptyStateComponent,
    FieldErrorComponent,
    BookCardComponent,
    PaginationComponent,
    ConfirmDialogComponent,
  ],
})
export class SharedModule {}
