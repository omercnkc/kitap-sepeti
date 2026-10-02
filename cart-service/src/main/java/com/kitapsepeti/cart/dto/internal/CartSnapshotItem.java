package com.kitapsepeti.cart.dto.internal;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** @param unitPriceSnapshot sepete eklendiği (ya da en son yeniden eklendiği) andaki fiyat, scale 2, JSON sayı */
public record CartSnapshotItem(
		@Schema(requiredMode = REQUIRED) UUID bookId,
		@Schema(requiredMode = REQUIRED, minimum = "1", maximum = "99") int quantity,
		@Schema(requiredMode = REQUIRED, description = "Sepete eklendiği (ya da en son yeniden eklendiği) andaki "
				+ "birim fiyat, 2 ondalık basamak.") BigDecimal unitPriceSnapshot,
		@Schema(requiredMode = REQUIRED, description = "`unitPriceSnapshot`'ın para birimi.") String currency,
		@Schema(requiredMode = REQUIRED, description = "Sepete eklendiği andaki başlık.") String title) {
}
