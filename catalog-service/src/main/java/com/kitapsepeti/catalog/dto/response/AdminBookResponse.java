package com.kitapsepeti.catalog.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Admin kitap detayı (her durumda döner). {@code version}, PATCH isteğinde geri gönderilmelidir.
 * {@code status}: draft | published | archived.
 */
public record AdminBookResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String title,
		String isbn,
		String description,
		Integer pageCount,
		String coverUrl,
		@Schema(requiredMode = REQUIRED) BigDecimal priceAmount,
		@Schema(requiredMode = REQUIRED) String currency,
		@Schema(requiredMode = REQUIRED) int stockQuantity,
		@Schema(requiredMode = REQUIRED) int reservedQuantity,
		@Schema(requiredMode = REQUIRED) int availableQuantity,
		@Schema(requiredMode = REQUIRED, allowableValues = { "draft", "published", "archived" }) String status,
		Instant publishedAt,
		@Schema(requiredMode = REQUIRED) Long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt,
		@Schema(requiredMode = REQUIRED) List<AuthorRef> authors,
		@Schema(requiredMode = REQUIRED) List<CategoryRef> categories) {
}
