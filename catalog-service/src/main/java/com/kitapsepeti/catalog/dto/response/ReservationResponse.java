package com.kitapsepeti.catalog.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bir siparişin stok rezervasyonu. {@code status} küçük harf: {@code held}, {@code committed}, {@code released}.
 * Kalemler bookId'ye göre sıralı. {@code unitPrice} kitabın okunduğu andaki fiyatıdır (yeni rezervasyonda
 * rezervasyon anı); fiyat anlık görüntüsünü saklamak sipariş servisinin işidir.
 */
public record ReservationResponse(UUID orderId, String status, Instant expiresAt, List<Item> items) {

	public record Item(UUID bookId, String title, int quantity, String unitPrice, String currency) {
	}

}
