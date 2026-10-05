import { Pipe, PipeTransform } from '@angular/core';
import { OrderStatus } from '../../core/models';

const LABELS: Readonly<Record<OrderStatus, string>> = {
  pending: 'Ödeme bekleniyor',
  paid: 'Ödendi',
  failed: 'Başarısız',
  cancelled: 'İptal edildi',
};

@Pipe({ name: 'orderStatus' })
export class OrderStatusPipe implements PipeTransform {
  transform(status: OrderStatus | string | null | undefined): string {
    if (!status) {
      return '—';
    }
    return LABELS[status as OrderStatus] ?? status;
  }
}
