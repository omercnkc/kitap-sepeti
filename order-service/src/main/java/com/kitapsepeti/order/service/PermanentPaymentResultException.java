package com.kitapsepeti.order.service;

/** Yeniden denemeyle düzelmeyecek ödeme sonucu hatası; listener mesajı doğrudan DLQ'ya yollar. */
public class PermanentPaymentResultException extends RuntimeException {

	public enum Reason {
		ORDER_NOT_FOUND,
		AMOUNT_MISMATCH,
		CURRENCY_MISMATCH
	}

	private final Reason reason;

	public PermanentPaymentResultException(Reason reason) {
		super(reason.name());
		this.reason = reason;
	}

	public Reason reason() {
		return this.reason;
	}

}
