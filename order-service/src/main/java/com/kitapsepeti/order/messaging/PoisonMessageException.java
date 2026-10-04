package com.kitapsepeti.order.messaging;

/** Yeniden denemeyle düzelmeyecek RabbitMQ mesajı. Mesaj gövdesi ve kimlikler exception'a konmaz. */
public class PoisonMessageException extends RuntimeException {

	public enum Reason {
		MALFORMED_JSON,
		UNKNOWN_TYPE,
		UNSUPPORTED_VERSION,
		MISSING_FIELD,
		INVALID_FIELD,
		ORDER_NOT_FOUND,
		AMOUNT_MISMATCH,
		CURRENCY_MISMATCH
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
