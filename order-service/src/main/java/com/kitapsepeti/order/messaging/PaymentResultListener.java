package com.kitapsepeti.order.messaging;

import java.util.concurrent.TimeUnit;

import com.kitapsepeti.order.config.PaymentResultsConsumerConfig;
import com.kitapsepeti.order.entity.OrderReasons;
import com.kitapsepeti.order.messaging.PoisonMessageException.Reason;
import com.kitapsepeti.order.service.OrderTransactions;
import com.kitapsepeti.order.service.OrderTransactions.PaymentOutcome;
import com.kitapsepeti.order.service.OrderTransactions.PaymentTransition;
import com.kitapsepeti.order.service.PermanentPaymentResultException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** PaymentSucceeded/PaymentFailed olaylarını sipariş durumuna uygular. */
@Component
@ConditionalOnProperty(prefix = "app.payment-results", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentResultListener {

	private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

	private final PaymentResultMessageParser parser;

	private final OrderTransactions transactions;

	public PaymentResultListener(PaymentResultMessageParser parser, OrderTransactions transactions) {
		this.parser = parser;
		this.transactions = transactions;
	}

	@RabbitListener(id = PaymentResultsConsumerConfig.LISTENER_ID, queues = PaymentResultsConsumerConfig.QUEUE,
			containerFactory = "paymentResultsListenerContainerFactory")
	public void onMessage(Message raw) {
		long start = System.nanoTime();
		String type = safeType(raw);
		String outcome = "RETRY";
		try {
			PaymentResultMessage message = this.parser.parse(raw);
			PaymentTransition transition = switch (message) {
				case PaymentResultMessage.Succeeded succeeded -> this.transactions.applyPaymentSucceeded(
						succeeded.orderId(), succeeded.paymentId(), succeeded.amount(), succeeded.currency());
				case PaymentResultMessage.Failed failed -> this.transactions.applyPaymentFailed(failed.orderId(),
						failed.paymentId(), failed.amount(), failed.currency(), OrderReasons.paymentFailureCode(failed.failureCode()));
			};
			outcome = transition.outcome().name();
			logConflict(type, transition.outcome());
		}
		catch (PermanentPaymentResultException ex) {
			outcome = "DLQ_" + ex.reason().name();
			throw new PoisonMessageException(switch (ex.reason()) {
				case ORDER_NOT_FOUND -> Reason.ORDER_NOT_FOUND;
				case AMOUNT_MISMATCH -> Reason.AMOUNT_MISMATCH;
				case CURRENCY_MISMATCH -> Reason.CURRENCY_MISMATCH;
			});
		}
		catch (PoisonMessageException ex) {
			outcome = "DLQ_" + ex.reason().name();
			throw ex;
		}
		finally {
			log.info("Payment result -> {} (type={}, durationMs={})", outcome, type,
					TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
		}
	}

	private static String safeType(Message message) {
		String type = message.getMessageProperties().getType();
		if (PaymentResultMessageParser.SUCCEEDED.equals(type) || PaymentResultMessageParser.FAILED.equals(type)) {
			return type;
		}
		return "Unknown";
	}

	private static void logConflict(String type, PaymentOutcome outcome) {
		switch (outcome) {
			case LATE_PAYMENT_SUCCESS ->
				log.error("Payment result conflict -> LATE_PAYMENT_SUCCESS (type={})", type);
			case LATE_PAYMENT_ID_CONFLICT -> {
				log.error("Payment result conflict -> LATE_PAYMENT_SUCCESS (type={})", type);
				log.error("Payment result conflict -> PAYMENT_ID_CONFLICT (type={})", type);
			}
			case PAYMENT_ID_CONFLICT ->
				log.error("Payment result conflict -> PAYMENT_ID_CONFLICT (type={})", type);
			case CONFLICTING_FINAL ->
				log.error("Payment result conflict -> CONFLICTING_FINAL (type={})", type);
			case APPLIED, ALREADY_IN_STATE -> {
			}
		}
	}

}
