package com.kitapsepeti.order.client.payment;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Payment {@code CreatePaymentRequest}. */
public record CreatePaymentRequest(UUID orderId, UUID userId, BigDecimal amount, String currency) {

	public CreatePaymentRequest {
		Objects.requireNonNull(orderId, "orderId");
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(currency, "currency");
	}

	@Override
	public String toString() {
		return "CreatePaymentRequest[redacted]";
	}

}
