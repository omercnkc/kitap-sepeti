package com.kitapsepeti.cart.messaging;

import java.util.concurrent.TimeUnit;

import com.kitapsepeti.cart.config.CartCheckoutsConsumerConfig;
import com.kitapsepeti.cart.messaging.PoisonMessageException.Reason;
import com.kitapsepeti.cart.service.CartTransactions;
import com.kitapsepeti.cart.service.CartTransactions.CheckoutOutcome;
import com.kitapsepeti.cart.service.PermanentCheckoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Order'ın CartCheckedOut olayıyla eşleşen aktif sepeti kapatır (checked_out). */
@Component
@ConditionalOnProperty(prefix = "app.cart-checkouts", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CartCheckedOutListener {

	private static final Logger log = LoggerFactory.getLogger(CartCheckedOutListener.class);

	private final CartCheckedOutMessageParser parser;

	private final CartTransactions transactions;

	public CartCheckedOutListener(CartCheckedOutMessageParser parser, CartTransactions transactions) {
		this.parser = parser;
		this.transactions = transactions;
	}

	@RabbitListener(id = CartCheckoutsConsumerConfig.LISTENER_ID, queues = CartCheckoutsConsumerConfig.QUEUE,
			containerFactory = "cartCheckoutsListenerContainerFactory")
	public void onMessage(Message raw) {
		long start = System.nanoTime();
		String type = CartCheckoutDeadLetterRecoverer.safeType(raw);
		String outcome = "RETRY";
		try {
			CartCheckedOutMessage message = this.parser.parse(raw);
			CheckoutOutcome result = this.transactions.checkOut(message.cartId(), message.userId());
			outcome = result.name();
			logNonApplied(type, result);
		}
		catch (PermanentCheckoutException ex) {
			outcome = "DLQ_" + ex.reason().name();
			throw new PoisonMessageException(switch (ex.reason()) {
				case CART_NOT_FOUND -> Reason.CART_NOT_FOUND;
				case CART_OWNER_MISMATCH -> Reason.CART_OWNER_MISMATCH;
			});
		}
		catch (PoisonMessageException ex) {
			outcome = "DLQ_" + ex.reason().name();
			throw ex;
		}
		finally {
			log.info("Cart checkout -> {} (type={}, durationMs={})", outcome, type,
					TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
		}
	}

	private static void logNonApplied(String type, CheckoutOutcome outcome) {
		switch (outcome) {
			case ALREADY_CHECKED_OUT -> log.debug("Cart checkout already applied (type={})", type);
			// Terk edilmiş sepeti kapatan bir görev henüz yok; ileride olursa sıra yarışı burada görünür.
			case CART_ABANDONED -> log.warn("Cart checkout -> CART_ABANDONED, cart unchanged (type={})", type);
			case CHECKED_OUT -> {
			}
		}
	}

}
