package com.kitapsepeti.cart.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Order'ın {@code CartCheckedOut} olayı (v1; routing key {@code cart.checked-out}). Olayda satır listesi yok: sepet
 * olduğu gibi kapanır.
 */
record CartCheckedOutMessage(UUID eventId, UUID cartId, UUID userId, UUID orderId, Instant occurredAt) {
}
