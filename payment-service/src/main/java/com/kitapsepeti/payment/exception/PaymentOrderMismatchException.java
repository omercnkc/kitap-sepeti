package com.kitapsepeti.payment.exception;

import com.kitapsepeti.common.error.ApiException;

/**
 * Siparişin ödemesi var ama tekrar gelen istek farklı kullanıcı, tutar ya da para birimi taşıyor (409). Mevcut ödeme
 * değişmez; yanıtta hangi alanın farklı olduğu ve değerler yer almaz.
 */
public class PaymentOrderMismatchException extends ApiException {

	public PaymentOrderMismatchException() {
		super(PaymentErrorCode.PAYMENT_ORDER_MISMATCH);
	}

}
