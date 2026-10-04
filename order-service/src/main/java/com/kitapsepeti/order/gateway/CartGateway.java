package com.kitapsepeti.order.gateway;

import java.util.UUID;

/** Cart ile tek temas noktası (salt okunur). Exception atmaz; teknik hatalar {@link Unavailable}. */
public interface CartGateway {

	/** Kullanıcının aktif sepetinin satırları (Cart {@code POST /internal/cart/snapshot}). */
	CartSnapshotResult snapshot(UUID userId);

}
