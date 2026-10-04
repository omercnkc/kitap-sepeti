package com.kitapsepeti.order.service.event;

import java.time.Instant;
import java.util.UUID;

/** {@code OrderPaid} outbox payload'ı. Para, MySQL JSON'da ondalık kaybı olmaması için metindir. */
public record OrderPaidEvent(UUID eventId, int eventVersion, UUID orderId, UUID userId, UUID paymentId,
		String totalAmount, String currency, int itemCount, Instant occurredAt) {

	public static final int VERSION = 1;

	public static final String TYPE = "OrderPaid";

}
