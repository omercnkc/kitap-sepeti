import { OrderStatusPipe } from './order-status.pipe';

describe('OrderStatusPipe', () => {
  const pipe = new OrderStatusPipe();

  it('maps known statuses to Turkish labels', () => {
    expect(pipe.transform('pending')).toBe('Ödeme bekleniyor');
    expect(pipe.transform('paid')).toBe('Ödendi');
    expect(pipe.transform('failed')).toBe('Başarısız');
    expect(pipe.transform('cancelled')).toBe('İptal edildi');
  });

  it('falls back for unknown or empty', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform('other')).toBe('other');
  });
});
