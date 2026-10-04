package com.kitapsepeti.payment.service;

import java.math.RoundingMode;
import java.util.UUID;

import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.service.event.PaymentFailedEvent;
import com.kitapsepeti.payment.service.event.PaymentSucceededEvent;
import org.springframework.stereotype.Component;

/**
 * Ödeme sonucu olaylarının payload'larını kurar. {@code occurredAt} geçişin {@code updated_at}'idir (Clock'tan,
 * mikrosaniye), olayın yazıldığı an değil.
 */
@Component
public class PaymentEventFactory {

	/** Outbox {@code aggregate_type}; catalog'daki {@code "book"}, user-service'teki {@code "user"} gibi küçük harf. */
	public static final String PAYMENT_AGGREGATE = "payment";

	public PaymentSucceededEvent succeeded(UUID eventId, Payment payment) {
		return new PaymentSucceededEvent(PaymentSucceededEvent.VERSION, eventId, payment.getId(), payment.getOrderId(),
				amount(payment), payment.getCurrency(), payment.getUpdatedAt());
	}

	public PaymentFailedEvent failed(UUID eventId, Payment payment) {
		return new PaymentFailedEvent(PaymentFailedEvent.VERSION, eventId, payment.getId(), payment.getOrderId(),
				amount(payment), payment.getCurrency(), payment.getFailureCode(), payment.getUpdatedAt());
	}

	private static String amount(Payment payment) {
		return payment.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
	}

}
