import { Injectable } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { from, Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  ConfirmDialogComponent,
  ConfirmDialogOptions,
} from './confirm-dialog.component';

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  constructor(private readonly modal: NgbModal) {}

  confirm(options: ConfirmDialogOptions): Observable<boolean> {
    const ref = this.modal.open(ConfirmDialogComponent, {
      centered: true,
      backdrop: 'static',
    });
    const instance = ref.componentInstance as ConfirmDialogComponent;
    instance.title = options.title;
    instance.message = options.message;
    instance.confirmLabel = options.confirmLabel ?? 'Onayla';
    instance.cancelLabel = options.cancelLabel ?? 'Vazgeç';
    instance.confirmButtonClass = options.confirmButtonClass ?? 'btn-danger';

    return from(ref.result).pipe(
      map(() => true),
      catchError(() => of(false)),
    );
  }
}
