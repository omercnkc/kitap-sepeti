package com.kitapsepeti.common.outbox;

/** Mesaj broker'a teslim edilemedi (nack, onay zaman aşımı veya kesinti); satır sonraki tura kalır. */
public class OutboxPublishException extends RuntimeException {

	public OutboxPublishException(String message) {
		super(message);
	}

	public OutboxPublishException(String message, Throwable cause) {
		super(message, cause);
	}

}
