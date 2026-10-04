package com.kitapsepeti.order.gateway;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** {@link CatalogGateway#lookup} sonucu. */
public sealed interface BookLookupResult permits BookLookupResult.Found, Unavailable {

	/**
	 * İstenen her kitap ya {@code books}'ta ya {@code notFound}'da (ikisinde birden değil).
	 *
	 * @param books yayındaki kitaplar (stokta olmayanlar dahil; bkz. {@link CatalogBook#inStock()})
	 * @param notFound Catalog'da olmayan ya da yayında olmayan (taslak/arşiv) kitaplar
	 */
	record Found(Map<UUID, CatalogBook> books, Set<UUID> notFound) implements BookLookupResult {

		public Found {
			books = Map.copyOf(books);
			notFound = Set.copyOf(notFound);
		}

		/** Yayında ve stokta olan kitap; değilse boş. */
		public Optional<CatalogBook> sellable(UUID bookId) {
			return Optional.ofNullable(this.books.get(bookId)).filter(CatalogBook::inStock);
		}

		@Override
		public String toString() {
			return "Found[books=" + this.books.size() + ", notFound=" + this.notFound.size() + "]";
		}

	}

}
