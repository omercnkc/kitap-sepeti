package com.kitapsepeti.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import org.junit.jupiter.api.Test;

class MockPaymentProviderTest {

	private final MockPaymentProvider provider = new MockPaymentProvider();

	@Test
	void typeIsMock() {
		assertThat(provider.type()).isEqualTo(PaymentProviderType.MOCK);
	}

	@Test
	void createReturnsUniqueMockReferenceWithoutRedirect() {
		ProviderPaymentRequest request = new ProviderPaymentRequest(UUID.randomUUID(), new BigDecimal("10.00"), "TRY");

		ProviderPayment first = provider.create(request);
		ProviderPayment second = provider.create(request);

		assertThat(first.redirectUrl()).isNull();
		assertThat(first.providerPaymentId()).startsWith("mock_")
			.hasSizeLessThanOrEqualTo(Payment.PROVIDER_REFERENCE_MAX_LENGTH);
		assertThat(UUID.fromString(first.providerPaymentId().substring("mock_".length()))).isNotNull();
		assertThat(second.providerPaymentId()).isNotEqualTo(first.providerPaymentId());
	}

}
