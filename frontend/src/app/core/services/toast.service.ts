import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export type ToastKind = 'success' | 'error' | 'info';

export interface ToastMessage {
  id: number;
  kind: ToastKind;
  text: string;
  delay: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  private readonly toastsSubject = new BehaviorSubject<ToastMessage[]>([]);

  readonly toasts$: Observable<ToastMessage[]> = this.toastsSubject.asObservable();

  success(text: string, delay = 4000): void {
    this.push('success', text, delay);
  }

  error(text: string, delay = 6000): void {
    this.push('error', text, delay);
  }

  info(text: string, delay = 4000): void {
    this.push('info', text, delay);
  }

  remove(id: number): void {
    this.toastsSubject.next(this.toastsSubject.value.filter((t) => t.id !== id));
  }

  private push(kind: ToastKind, text: string, delay: number): void {
    const toast: ToastMessage = { id: this.nextId++, kind, text, delay };
    this.toastsSubject.next([...this.toastsSubject.value, toast]);
  }
}
