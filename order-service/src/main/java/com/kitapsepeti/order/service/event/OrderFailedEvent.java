package com.kitapsepeti.order.service.event;

import java.time.Instant;
import java.util.UUID;

/** {@code OrderFailed} outbox payload'ı. */
public record OrderFailedEvent(UUID eventId, int eventVersion, UUID orderId, UUID userId, String failureCode,
		Instant occurredAt) {

	public static final int VERSION = 1;

	public static final String TYPE = "OrderFailed";

}
