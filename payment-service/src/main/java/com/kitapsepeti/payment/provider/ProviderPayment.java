package com.kitapsepeti.payment.provider;

import java.util.Objects;

/**
 * Sağlayıcının oluşturduğu ödeme.
 *
 * @param providerPaymentId sağlayıcıdaki referans ({@code payments.provider_payment_id})
 * @param redirectUrl kullanıcının yönlendirileceği ödeme sayfası; gerekmiyorsa null (mock)
 */
public record ProviderPayment(String providerPaymentId, String redirectUrl) {

	public ProviderPayment {
		Objects.requireNonNull(providerPaymentId, "providerPaymentId");
	}

}
