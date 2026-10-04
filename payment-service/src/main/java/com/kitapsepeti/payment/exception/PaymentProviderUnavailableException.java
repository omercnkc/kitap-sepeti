package com.kitapsepeti.payment.exception;

import com.kitapsepeti.common.error.ApiException;

/**
 * Sağlayıcıda ödeme oluşturulamadı (503). Ödeme referanssız {@code initiated} kalır; aynı istek tekrarlanınca
 * sağlayıcı yeniden çağrılır. Neden yalnızca loglanır (sınıf adı), yanıta girmez.
 */
public class PaymentProviderUnavailableException extends ApiException {

	public PaymentProviderUnavailableException(Throwable cause) {
		super(PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
		initCause(cause);
	}

}
