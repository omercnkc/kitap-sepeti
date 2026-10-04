package com.kitapsepeti.order.gateway;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** {@link CartGateway#snapshot} sonucu. */
public sealed interface CartSnapshotResult permits CartSnapshotResult.Snapshot, CartSnapshotResult.Empty, Unavailable {

	/**
	 * Aktif ve dolu sepet. Fiyat bilerek yok: sipariş fiyatı Catalog'dan okunur.
	 *
	 * @param lines sepete eklenme sırasıyla, en az bir satır, her kitap bir kez
	 */
	record Snapshot(UUID cartId, List<StockLine> lines) implements CartSnapshotResult {

		public Snapshot {
			Objects.requireNonNull(cartId, "cartId");
			lines = List.copyOf(lines);
		}

		@Override
		public String toString() {
			return "Snapshot[lines=" + this.lines.size() + "]";
		}

	}

	/** Aktif sepet yok ya da boş. */
	record Empty() implements CartSnapshotResult {
	}

}
