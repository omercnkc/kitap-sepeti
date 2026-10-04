-- Sağlayıcı kimlikleri büyük/küçük harfe duyarlı karşılaştırılır (ör. Stripe 'pi_3Abc' ile 'pi_3abc' farklı ödemelerdir).
-- V1'de bu iki kolon tablo varsayılanı utf8mb4_0900_ai_ci idi: referans araması harf büyüklüğünü yok sayıyor ve
-- uk_payments_provider_ref / uk_provider_events_provider_event yalnızca harf büyüklüğü farklı iki kimliği çakıştırıyordu.
-- MODIFY kolonu yeniden tanımlar; UNIQUE kısıtlar (ad ve kolon listesi) aynen kalır. _bin, _ai_ci'den katı olduğu için
-- mevcut satırlar yeni bir çakışma üretemez.
ALTER TABLE payments
    MODIFY provider_payment_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL;

ALTER TABLE provider_events
    MODIFY provider_event_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
