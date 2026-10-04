package com.kitapsepeti.payment.provider;

import java.util.UUID;

import com.kitapsepeti.payment.entity.PaymentProviderType;

/** Geliştirme sağlayıcısı: HTTP yok, yönlendirme yok; referans {@code mock_<uuid>}. Sonuç kuralı {@link MockOutcomeRule}'da. */
public class MockPaymentProvider implements PaymentProvider {

	static final String REFERENCE_PREFIX = "mock_";

	@Override
	public PaymentProviderType type() {
		return PaymentProviderType.MOCK;
	}

	@Override
	public ProviderPayment create(ProviderPaymentRequest request) {
		return new ProviderPayment(REFERENCE_PREFIX + UUID.randomUUID(), null);
	}

}
