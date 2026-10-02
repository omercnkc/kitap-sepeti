package com.kitapsepeti.cart.dto.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kullanıcının aktif sepetinin anlık görüntüsü (Catalog'a sorulmadan, sepete eklendiği andaki fiyatlarla).
 * Aktif sepet yoksa {@code cartId} ve {@code updatedAt} null, {@code items} boş; aktif sepet boşsa yalnızca
 * {@code items} boş. userId bilerek yok.
 */
public record CartSnapshotResponse(UUID cartId, Instant updatedAt, List<CartSnapshotItem> items) {

	public static final CartSnapshotResponse NO_ACTIVE_CART = new CartSnapshotResponse(null, null, List.of());

	public CartSnapshotResponse {
		items = List.copyOf(items);
	}

}
