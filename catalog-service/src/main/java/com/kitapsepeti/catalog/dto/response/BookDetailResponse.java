package com.kitapsepeti.catalog.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kitap detayı: {@link BookSummaryResponse} alanları + künye ve kategoriler. Stok miktarları, versiyon, durum ve
 * kayıt zaman damgaları dışarı verilmez. {@code authors} ve {@code categories} ada göre sıralı.
 */
public record BookDetailResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String title,
		String coverUrl,
		@Schema(requiredMode = REQUIRED) BigDecimal priceAmount,
		@Schema(requiredMode = REQUIRED) String currency,
		@Schema(requiredMode = REQUIRED) boolean inStock,
		@Schema(requiredMode = REQUIRED) PublisherRef publisher,
		@Schema(requiredMode = REQUIRED) List<AuthorRef> authors,
		String isbn,
		String description,
		Integer pageCount,
		Instant publishedAt,
		@Schema(requiredMode = REQUIRED) List<CategoryRef> categories) {
}
