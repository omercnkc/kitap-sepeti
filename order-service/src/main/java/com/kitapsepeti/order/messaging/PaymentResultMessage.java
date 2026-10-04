package com.kitapsepeti.order.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

sealed interface PaymentResultMessage permits PaymentResultMessage.Succeeded, PaymentResultMessage.Failed {

	UUID eventId();

	UUID paymentId();

	UUID orderId();

	BigDecimal amount();

	String currency();

	Instant occurredAt();

	record Succeeded(UUID eventId, UUID paymentId, UUID orderId, BigDecimal amount, String currency,
			Instant occurredAt) implements PaymentResultMessage {
	}

	record Failed(UUID eventId, UUID paymentId, UUID orderId, BigDecimal amount, String currency, String failureCode,
			Instant occurredAt) implements PaymentResultMessage {
	}

}
