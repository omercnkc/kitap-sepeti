package com.kitapsepeti.order.gateway;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link CatalogGateway#reserve} sonucu. Catalog'da rezervasyon ya hep ya hiç: iş hatalarında hiçbir kalem ayrılmaz.
 */
public sealed interface ReserveResult permits ReserveResult.Reserved, ReserveResult.Insufficient,
		ReserveResult.NotSellable, ReserveResult.NotHeld, Rejected, NotPerformed, Unknown {

	/** Stok ayrıldı ({@code held}): yeni (201) ya da aynı sipariş aynı kalemlerle tekrar (200). */
	record Reserved(Instant expiresAt) implements ReserveResult {

		public Reserved {
			Objects.requireNonNull(expiresAt, "expiresAt");
		}

	}

	/** 409 {@code INSUFFICIENT_STOCK}: en az bir kitabın satılabilir stoğu yetmiyor. */
	record Insufficient(List<UUID> bookIds) implements ReserveResult {

		public Insufficient {
			bookIds = List.copyOf(bookIds);
		}

		@Override
		public String toString() {
			return "Insufficient[books=" + this.bookIds.size() + "]";
		}

	}

	/** 409 {@code BOOK_NOT_AVAILABLE}: en az bir kitap yok ya da yayında değil (stok sorunlarından önceliklidir). */
	record NotSellable(List<UUID> bookIds) implements ReserveResult {

		public NotSellable {
			bookIds = List.copyOf(bookIds);
		}

		@Override
		public String toString() {
			return "NotSellable[books=" + this.bookIds.size() + "]";
		}

	}

	/**
	 * Siparişin aynı kalemlerle rezervasyonu zaten var ama artık {@code held} değil (200 + {@code committed} ya da
	 * {@code released}; ör. süre dolumundan sonra tekrar istek). Stok bu sipariş için TUTULMUYOR olabilir.
	 */
	record NotHeld(ReservationStatus status) implements ReserveResult {

		public NotHeld {
			Objects.requireNonNull(status, "status");
		}

	}

}
