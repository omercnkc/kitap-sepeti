package com.kitapsepeti.payment.provider;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Sağlayıcıya giden ödeme isteği. Sipariş ve kullanıcı kimliği bilerek yok: sağlayıcı yalnızca bizim ödeme id'mizi görür.
 *
 * @param paymentId {@code payments.id} (sağlayıcı tarafında bizim referansımız)
 */
public record ProviderPaymentRequest(UUID paymentId, BigDecimal amount, String currency) {

	public ProviderPaymentRequest {
		Objects.requireNonNull(paymentId, "paymentId");
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(currency, "currency");
	}

}
