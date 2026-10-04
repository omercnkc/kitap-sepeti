package com.kitapsepeti.order.gateway;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Catalog'da yayında olan bir kitabın okunduğu andaki hali. {@link #toString()} kimlik, başlık ve fiyat içermez.
 *
 * @param inStock Catalog {@code inStock}: satılabilir adet &gt; 0. İstenen adedin yeteceğini GARANTİ ETMEZ; kesin
 *        kontrol stok rezervasyonudur ({@link ReserveResult.Insufficient}).
 */
public record CatalogBook(UUID bookId, String title, BigDecimal unitPrice, String currency, boolean inStock) {

	public CatalogBook {
		Objects.requireNonNull(bookId, "bookId");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(unitPrice, "unitPrice");
		Objects.requireNonNull(currency, "currency");
	}

	@Override
	public String toString() {
		return "CatalogBook[redacted]";
	}

}
