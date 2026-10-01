package com.kitapsepeti.catalog.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Listede kitap. Stok miktarları dışarı verilmez; yalnızca {@code inStock} (satılabilir stok &gt; 0).
 * {@code authors} ada göre sıralı.
 */
public record BookSummaryResponse(UUID id, String title, String coverUrl, BigDecimal priceAmount, String currency,
		boolean inStock, PublisherRef publisher, List<AuthorRef> authors) {
}
