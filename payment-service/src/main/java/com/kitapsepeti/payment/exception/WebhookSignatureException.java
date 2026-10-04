package com.kitapsepeti.payment.exception;

import com.kitapsepeti.common.error.ApiException;
import com.kitapsepeti.payment.entity.PaymentProviderType;

/**
 * Webhook imzası yok ya da geçersiz (401 + {@value #CHALLENGE}). Eksik başlık, eski damga ve tutmayan imza aynı
 * exception'dır; yanıt ve log hangisi olduğunu söylemez.
 */
public class WebhookSignatureException extends ApiException {

	public static final String CHALLENGE = "Signature realm=\"webhook\"";

	private final PaymentProviderType provider;

	public WebhookSignatureException(PaymentProviderType provider) {
		super(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID);
		this.provider = provider;
	}

	public PaymentProviderType getProvider() {
		return this.provider;
	}

}
