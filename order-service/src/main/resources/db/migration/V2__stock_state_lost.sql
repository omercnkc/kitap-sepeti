-- V2: stock_state 'lost' desteği (yalnızca ödenmiş siparişler için).
-- MySQL 8.0'da CHECK kısıtları DROP CHECK + ADD CONSTRAINT ile güncellenir.

ALTER TABLE orders
    DROP CHECK ck_orders_stock_state,
    ADD CONSTRAINT ck_orders_stock_state CHECK (stock_state IN ('requested', 'held', 'committed', 'released', 'lost'));

ALTER TABLE orders
    DROP CHECK ck_orders_paid_stock,
    ADD CONSTRAINT ck_orders_paid_stock CHECK (status <> 'paid' OR stock_state IN ('held', 'committed', 'lost'));

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_lost_paid CHECK (stock_state <> 'lost' OR status = 'paid');
