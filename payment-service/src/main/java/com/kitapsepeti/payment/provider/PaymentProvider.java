package com.kitapsepeti.payment.provider;

import com.kitapsepeti.payment.entity.PaymentProviderType;

/** Ödeme sağlayıcısı. Uygulamada tek bean vardır; {@code app.payment.provider} ile seçilir. */
public interface PaymentProvider {

	PaymentProviderType type();

	/** Sağlayıcıda ödemeyi oluşturur; sonuç sonradan webhook ile gelir. */
	ProviderPayment create(ProviderPaymentRequest request);

}
