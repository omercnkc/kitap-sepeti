package com.kitapsepeti.catalog.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Admin kitap listesi satırı; public yanıtlardan farklı olarak stok miktarları, durum ve versiyon içerir. */
public record AdminBookSummaryResponse(UUID id, String title,
		@Schema(allowableValues = { "draft", "published", "archived" }) String status, BigDecimal priceAmount,
		String currency, int stockQuantity, int reservedQuantity, int availableQuantity, PublisherRef publisher,
		Instant updatedAt, Long version) {
}
