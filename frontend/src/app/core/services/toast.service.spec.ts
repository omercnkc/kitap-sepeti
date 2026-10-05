import { take } from 'rxjs/operators';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    service = new ToastService();
  });

  it('should emit success toast', () => {
    service.success('Tamam');
    let kind = '';
    let text = '';
    service.toasts$.pipe(take(1)).subscribe((toasts) => {
      kind = toasts[0].kind;
      text = toasts[0].text;
    });
    expect(kind).toBe('success');
    expect(text).toBe('Tamam');
  });

  it('should remove toast by id', () => {
    service.error('Hata');
    let id = -1;
    service.toasts$.pipe(take(1)).subscribe((toasts) => {
      id = toasts[0].id;
    });
    service.remove(id);
    let length = -1;
    service.toasts$.pipe(take(1)).subscribe((toasts) => {
      length = toasts.length;
    });
    expect(length).toBe(0);
  });
});
