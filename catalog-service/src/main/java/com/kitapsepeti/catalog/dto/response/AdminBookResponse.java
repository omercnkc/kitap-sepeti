package com.kitapsepeti.catalog.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin kitap detayı (her durumda döner). {@code version}, PATCH isteğinde geri gönderilmelidir.
 * {@code status}: draft | published | archived.
 */
public record AdminBookResponse(UUID id, String title, String isbn, String description, Integer pageCount,
		String coverUrl, BigDecimal priceAmount, String currency, int stockQuantity, int reservedQuantity,
		int availableQuantity, String status, Instant publishedAt, Long version, Instant createdAt,
		Instant updatedAt, PublisherRef publisher, List<AuthorRef> authors, List<CategoryRef> categories) {
}
