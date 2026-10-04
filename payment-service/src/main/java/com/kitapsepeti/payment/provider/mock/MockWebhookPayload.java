package com.kitapsepeti.payment.provider.mock;

import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.ProviderEventType;
import com.kitapsepeti.payment.provider.MockOutcome;

/**
 * Mock webhook gövdesi; alanlar webhook ucunun sözleşmesiyle ({@code WebhookEvent}) aynıdır, hepsi JSON metni.
 * {@code failureCode} yalnızca {@code payment.failed}'da yazılır.
 *
 * @param eventId ödeme başına sabit: aynı ödeme için her gönderim aynı olaydır, tekrarı webhook ucu yok sayar
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record MockWebhookPayload(String eventId, String providerPaymentId, String type, String amount, String currency,
		String failureCode) {

	static final String EVENT_ID_PREFIX = "mock_evt_";

	static MockWebhookPayload of(Payment payment, MockOutcome outcome) {
		ProviderEventType type = outcome.isSucceeded() ? ProviderEventType.PAYMENT_SUCCEEDED
				: ProviderEventType.PAYMENT_FAILED;
		return new MockWebhookPayload(EVENT_ID_PREFIX + payment.getId(), payment.getProviderPaymentId(),
				type.dbValue(), payment.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
				payment.getCurrency(), outcome.failureCode());
	}

}
