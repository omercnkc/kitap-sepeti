package com.kitapsepeti.cart.messaging;

/** Yeniden denemeyle düzelmeyecek RabbitMQ mesajı. Mesaj gövdesi ve kimlikler exception'a konmaz. */
public class PoisonMessageException extends RuntimeException {

	public enum Reason {
		MALFORMED_JSON,
		UNKNOWN_TYPE,
		UNSUPPORTED_VERSION,
		MISSING_FIELD,
		INVALID_FIELD,
		CART_NOT_FOUND,
		CART_OWNER_MISMATCH
	}

	private final Reason reason;

	public PoisonMessageException(Reason reason) {
		super(reason.name());
		this.reason = reason;
	}

	public Reason reason() {
		return this.reason;
	}

}
