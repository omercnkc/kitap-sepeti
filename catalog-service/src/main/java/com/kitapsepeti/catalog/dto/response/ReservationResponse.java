package com.kitapsepeti.catalog.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Bir siparişin stok rezervasyonu. {@code status} küçük harf: {@code held}, {@code committed}, {@code released}.
 * Kalemler bookId'ye göre sıralı. {@code unitPrice} kitabın okunduğu andaki fiyatıdır (yeni rezervasyonda
 * rezervasyon anı); fiyat anlık görüntüsünü saklamak sipariş servisinin işidir.
 */
public record ReservationResponse(
		@Schema(requiredMode = REQUIRED) UUID orderId,
		@Schema(requiredMode = REQUIRED, allowableValues = { "held", "committed", "released" }) String status,
		@Schema(requiredMode = REQUIRED) Instant expiresAt,
		@Schema(requiredMode = REQUIRED) List<Item> items) {

	@Schema(name = "ReservationItem")
	public record Item(
			@Schema(requiredMode = REQUIRED) UUID bookId,
			@Schema(requiredMode = REQUIRED) String title,
			@Schema(requiredMode = REQUIRED) int quantity,
			@Schema(requiredMode = REQUIRED, description = "Kitabın okunduğu andaki birim fiyatı; `priceAmount` ile "
					+ "aynı biçimde sayı, 2 ondalık") BigDecimal unitPrice,
			@Schema(requiredMode = REQUIRED) String currency) {
	}

}
