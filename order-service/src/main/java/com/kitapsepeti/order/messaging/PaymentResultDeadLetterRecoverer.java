package com.kitapsepeti.order.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

/** Poison veya retry tüketmiş mesajı requeue etmeden reddeder; queue DLX argümanı mesajı DLQ'ya taşır. */
public class PaymentResultDeadLetterRecoverer implements MessageRecoverer {

	private static final Logger log = LoggerFactory.getLogger(PaymentResultDeadLetterRecoverer.class);

	@Override
	public void recover(Message message, Throwable cause) {
		PoisonMessageException poison = findPoison(cause);
		String type = safeType(message);
		String reason = poison == null ? "RETRY_EXHAUSTED" : poison.reason().name();
		if (poison != null && (poison.reason() == PoisonMessageException.Reason.AMOUNT_MISMATCH
				|| poison.reason() == PoisonMessageException.Reason.CURRENCY_MISMATCH)) {
			log.error("Payment result -> DLQ (type={}, reason={})", type, reason);
		}
		else {
			log.warn("Payment result -> DLQ (type={}, reason={})", type, reason);
		}
		throw new AmqpRejectAndDontRequeueException("Payment result rejected");
	}

	static PoisonMessageException findPoison(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof PoisonMessageException poison) {
				return poison;
			}
			current = current.getCause();
		}
		return null;
	}

	private static String safeType(Message message) {
		String type = message.getMessageProperties().getType();
		if (PaymentResultMessageParser.SUCCEEDED.equals(type) || PaymentResultMessageParser.FAILED.equals(type)) {
			return type;
		}
		return "Unknown";
	}

}
