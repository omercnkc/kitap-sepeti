-- V3: başarısız siparişe geç gelen başarılı ödemenin kaydı (iade listesi; v1'de otomatik iade yok).
-- Sipariş failed kalır; late_payment_at ödemenin Order'a ulaştığı ilk an (uygulama Clock'u, UTC).

ALTER TABLE orders
    ADD COLUMN late_payment_at DATETIME(6) NULL AFTER failure_code;

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_late_payment_failed CHECK (late_payment_at IS NULL OR status = 'failed');
