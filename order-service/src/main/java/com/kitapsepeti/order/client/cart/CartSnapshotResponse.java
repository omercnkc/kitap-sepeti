package com.kitapsepeti.order.client.cart;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cart {@code CartSnapshotResponse}: yalnızca Order'ın okuduğu alanlar. Sepet fiyatı/başlığı okunmaz (sipariş fiyatı
 * Catalog'dan). Bilinmeyen alanlar yok sayılır; zorunlu alan eksikse çözümleme başarısız olur (teknik hata).
 *
 * @param cartId aktif sepet yoksa null (alan yine de zorunlu)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CartSnapshotResponse(
		@JsonProperty(required = true) UUID cartId,
		@JsonProperty(required = true) List<Item> items) {

	public CartSnapshotResponse {
		Objects.requireNonNull(items, "items");
		items = List.copyOf(items);
	}

	@Override
	public String toString() {
		return "CartSnapshotResponse[items=" + this.items.size() + "]";
	}

	/** Cart {@code CartSnapshotItem}. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Item(
			@JsonProperty(required = true) UUID bookId,
			@JsonProperty(required = true) Integer quantity) {

		public Item {
			Objects.requireNonNull(bookId, "bookId");
			Objects.requireNonNull(quantity, "quantity");
		}

		@Override
		public String toString() {
			return "Item[redacted]";
		}

	}

}
