package com.kitapsepeti.catalog.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Admin kitap listesi satırı; public yanıtlardan farklı olarak stok miktarları, durum ve versiyon içerir. */
public record AdminBookSummaryResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String title,
		@Schema(requiredMode = REQUIRED, allowableValues = { "draft", "published", "archived" }) String status,
		@Schema(requiredMode = REQUIRED) BigDecimal priceAmount,
		@Schema(requiredMode = REQUIRED) String currency,
		@Schema(requiredMode = REQUIRED) int stockQuantity,
		@Schema(requiredMode = REQUIRED) int reservedQuantity,
		@Schema(requiredMode = REQUIRED) int availableQuantity,
		@Schema(requiredMode = REQUIRED) PublisherRef publisher,
		@Schema(requiredMode = REQUIRED) Instant updatedAt,
		@Schema(requiredMode = REQUIRED) Long version) {
}
