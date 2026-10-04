package com.kitapsepeti.order.service.event;

import java.time.Instant;
import java.util.UUID;

/** {@code CartCheckedOut} outbox payload'ı; Cart Adım 7 consumer'ının sözleşme kaynağı olacaktır. */
public record CartCheckedOutEvent(UUID eventId, int eventVersion, UUID cartId, UUID userId, UUID orderId,
		Instant occurredAt) {

	public static final int VERSION = 1;

	public static final String TYPE = "CartCheckedOut";

}
