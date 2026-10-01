package com.kitapsepeti.catalog.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Listede kitap. Stok miktarları dışarı verilmez; yalnızca {@code inStock} (satılabilir stok &gt; 0).
 * {@code authors} ada göre sıralı.
 */
public record BookSummaryResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String title,
		String coverUrl,
		@Schema(requiredMode = REQUIRED) BigDecimal priceAmount,
		@Schema(requiredMode = REQUIRED) String currency,
		@Schema(requiredMode = REQUIRED) boolean inStock,
		@Schema(requiredMode = REQUIRED) PublisherRef publisher,
		@Schema(requiredMode = REQUIRED) List<AuthorRef> authors) {
}
