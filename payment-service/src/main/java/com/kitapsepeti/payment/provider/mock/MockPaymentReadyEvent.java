package com.kitapsepeti.payment.provider.mock;

import java.util.Objects;
import java.util.UUID;

/**
 * Mock ödemeye sağlayıcı referansı ilk kez yazıldı; webhook gönderilebilir. Uygulama içi olaydır (broker'a gitmez),
 * {@link MockWebhookDispatcher} transaction commit edildikten sonra dinler. Ödemenin geri kalanı gönderim anında
 * DB'den okunur.
 */
public record MockPaymentReadyEvent(UUID paymentId) {

	public MockPaymentReadyEvent {
		Objects.requireNonNull(paymentId, "paymentId");
	}

}
