package com.kitapsepeti.cart.service;

/** Yeniden denemeyle düzelmeyecek CartCheckedOut hatası; listener mesajı doğrudan DLQ'ya yollar. */
public class PermanentCheckoutException extends RuntimeException {

	public enum Reason {
		CART_NOT_FOUND,
		CART_OWNER_MISMATCH
	}

	private final Reason reason;

	public PermanentCheckoutException(Reason reason) {
		super(reason.name());
		this.reason = reason;
	}

	public Reason reason() {
		return this.reason;
	}

}
