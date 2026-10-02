package com.kitapsepeti.cart.dto.internal;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kullanıcının aktif sepetinin anlık görüntüsü (Catalog'a sorulmadan, sepete eklendiği andaki fiyatlarla).
 * Aktif sepet yoksa {@code cartId} ve {@code updatedAt} null, {@code items} boş; aktif sepet boşsa yalnızca
 * {@code items} boş. userId bilerek yok.
 */
public record CartSnapshotResponse(
		@Schema(requiredMode = REQUIRED, types = { "string", "null" }, format = "uuid",
				description = "Aktif sepetin id'si; aktif sepet yoksa null.") UUID cartId,
		@Schema(requiredMode = REQUIRED, types = { "string", "null" }, format = "date-time",
				description = "Sepetin son değiştiği an; aktif sepet yoksa null.") Instant updatedAt,
		@Schema(requiredMode = REQUIRED, description = "Satırlar, eklenme sırasıyla; sepet yoksa ya da boşsa boş.")
		List<CartSnapshotItem> items) {

	public static final CartSnapshotResponse NO_ACTIVE_CART = new CartSnapshotResponse(null, null, List.of());

	public CartSnapshotResponse {
		items = List.copyOf(items);
	}

}
