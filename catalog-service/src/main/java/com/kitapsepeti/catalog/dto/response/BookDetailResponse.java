package com.kitapsepeti.catalog.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kitap detayı: {@link BookSummaryResponse} alanları + künye ve kategoriler. Stok miktarları, versiyon, durum ve
 * kayıt zaman damgaları dışarı verilmez. {@code authors} ve {@code categories} ada göre sıralı.
 */
public record BookDetailResponse(UUID id, String title, String coverUrl, BigDecimal priceAmount, String currency,
		boolean inStock, PublisherRef publisher, List<AuthorRef> authors, String isbn, String description,
		Integer pageCount, Instant publishedAt, List<CategoryRef> categories) {
}
