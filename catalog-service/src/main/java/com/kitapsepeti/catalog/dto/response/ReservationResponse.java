package com.kitapsepeti.catalog.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Bir siparişin stok rezervasyonu. {@code status} küçük harf: {@code held}, {@code committed}, {@code released}.
 * Kalemler bookId'ye göre sıralı. {@code unitPrice} kitabın okunduğu andaki fiyatıdır (yeni rezervasyonda
 * rezervasyon anı); fiyat anlık görüntüsünü saklamak sipariş servisinin işidir.
 */
public record ReservationResponse(UUID orderId,
		@Schema(allowableValues = { "held", "committed", "released" }) String status, Instant expiresAt,
		List<Item> items) {

	@Schema(name = "ReservationItem")
	public record Item(UUID bookId, String title, int quantity,
			@Schema(description = "Ondalık metin, ör. \"129.90\"") String unitPrice, String currency) {
	}

}
