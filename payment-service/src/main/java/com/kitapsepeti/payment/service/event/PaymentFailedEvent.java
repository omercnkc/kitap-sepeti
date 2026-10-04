package com.kitapsepeti.payment.service.event;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code PaymentFailed} outbox olayının içeriği (bkz. {@code docs/events/payment-failed.md}).
 * {@link PaymentSucceededEvent} ile aynı alanlar + {@code failureCode} (ör. {@code CARD_DECLINED}).
 */
public record PaymentFailedEvent(int eventVersion, UUID eventId, UUID paymentId, UUID orderId, String amount,
		String currency, String failureCode, Instant occurredAt) {

	public static final int VERSION = 1;

	public static final String TYPE = "PaymentFailed";

}
