package com.kitapsepeti.cart.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

/** Poison veya retry tüketmiş mesajı requeue etmeden reddeder; queue DLX argümanı mesajı DLQ'ya taşır. */
public class CartCheckoutDeadLetterRecoverer implements MessageRecoverer {

	private static final Logger log = LoggerFactory.getLogger(CartCheckoutDeadLetterRecoverer.class);

	@Override
	public void recover(Message message, Throwable cause) {
		PoisonMessageException poison = findPoison(cause);
		String type = safeType(message);
		String reason = poison == null ? "RETRY_EXHAUSTED" : poison.reason().name();
		if (poison != null && poison.reason() == PoisonMessageException.Reason.CART_OWNER_MISMATCH) {
			log.error("Cart checkout -> DLQ (type={}, reason={})", type, reason);
		}
		else {
			log.warn("Cart checkout -> DLQ (type={}, reason={})", type, reason);
		}
		throw new AmqpRejectAndDontRequeueException("Cart checkout rejected");
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

	static String safeType(Message message) {
		return CartCheckedOutMessageParser.TYPE.equals(message.getMessageProperties().getType())
				? CartCheckedOutMessageParser.TYPE : "Unknown";
	}

}
