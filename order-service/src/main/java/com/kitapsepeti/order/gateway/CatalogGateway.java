package com.kitapsepeti.order.gateway;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Catalog ile tek temas noktası: kitap okuma (public) ve stok rezervasyonu (internal). Exception atmaz; teknik hatalar
 * okumada {@link Unavailable}, yazmada {@link NotPerformed} (gönderilmedi) ya da {@link Unknown} (sonuç bilinmiyor).
 * Rezervasyon işlemleri sipariş id'siyle idempotenttir (Catalog sözleşmesi): aynı istek güvenle tekrarlanabilir.
 */
public interface CatalogGateway {

	/** Catalog {@code GET /api/books/lookup} ve {@code POST /internal/stock/reservations} üst sınırı. */
	int MAX_BOOKS = 50;

	/**
	 * Kitapların güncel başlık, fiyat ve satılabilirliği. Boş koleksiyonda Catalog çağrılmaz.
	 *
	 * @throws IllegalArgumentException {@value #MAX_BOOKS}'den fazla tekil kitap
	 */
	BookLookupResult lookup(Collection<UUID> bookIds);

	/**
	 * Siparişin kalemleri için stok ayırır.
	 *
	 * @throws IllegalArgumentException kalem yok, {@value #MAX_BOOKS}'den fazla ya da aynı kitap iki kez
	 */
	ReserveResult reserve(UUID orderId, List<StockLine> lines);

	/** Ayrılan stoğu düşer (ödeme başarılı). */
	CommitResult commit(UUID orderId);

	/** Ayrılan stoğu geri verir (sipariş başarısız). */
	ReleaseResult release(UUID orderId);

}
