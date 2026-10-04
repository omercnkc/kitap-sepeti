package com.kitapsepeti.order.client.catalog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Catalog {@code ReserveStockRequest}. */
public record ReserveStockRequest(UUID orderId, List<Item> items) {

	public ReserveStockRequest {
		Objects.requireNonNull(orderId, "orderId");
		items = List.copyOf(items);
	}

	@Override
	public String toString() {
		return "ReserveStockRequest[items=" + this.items.size() + "]";
	}

	/** Catalog {@code ReserveStockItem}. */
	public record Item(UUID bookId, int quantity) {

		public Item {
			Objects.requireNonNull(bookId, "bookId");
		}

		@Override
		public String toString() {
			return "Item[redacted]";
		}

	}

}
