package com.kitapsepeti.catalog.service.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code BookUpserted} outbox olayının içeriği (bkz. {@code docs/events/book-upserted.md}): yayındaki kitabın
 * aramada/listelemede gereken güncel hali. Stok/rezerv miktarı, versiyon ve durum BİLİNÇLİ olarak yoktur;
 * yalnızca {@code inStock}. Fiyat, ondalık kaybı olmasın diye metindir ({@code "149.90"}): outbox payload kolonu
 * MySQL JSON ve sayıları DOUBLE saklar ({@code 149.90} → {@code 149.9}).
 * Alan eklemek geriye uyumludur; alan silmek veya anlamını değiştirmek {@code eventVersion}'ı artırmayı gerektirir.
 */
public record BookUpsertedEvent(int eventVersion, UUID bookId, String title, String isbn, String description,
		String priceAmount, String currency, String coverUrl, Integer pageCount, boolean inStock, Instant publishedAt,
		Ref publisher, List<Ref> authors, List<Ref> categories, List<UUID> categoryIdsWithAncestors,
		Instant occurredAt) {

	public static final int VERSION = 1;

	public static final String TYPE = "BookUpserted";

	/** Yayınevi/yazar/kategori özeti. */
	public record Ref(UUID id, String name, String slug) {
	}

}
