package com.kitapsepeti.payment.service;

import java.time.Clock;
import java.util.UUID;

import com.kitapsepeti.common.error.ResourceNotFoundException;
import com.kitapsepeti.common.outbox.OutboxService;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.TransitionResult;
import com.kitapsepeti.payment.repository.PaymentRepository;
import com.kitapsepeti.payment.service.event.PaymentFailedEvent;
import com.kitapsepeti.payment.service.event.PaymentSucceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ödeme sonucunu kaydeder: durum geçişi ve sonucu Order'a taşıyacak outbox olayı AYNI transaction'da
 * (biri geri alınırsa diğeri de alınır). Propagation REQUIRED: webhook (Adım 5) kendi transaction'ı içinden çağırır;
 * o durumda isolation dış transaction'ınkidir.
 * <p>
 * Ödeme {@code FOR UPDATE} ile okunur; aynı ödemeye eşzamanlı iki sonuç sıraya girer, ikincisi birincinin commit
 * ettiği durumu görür. Yalnızca {@link TransitionResult#APPLIED} olay üretir; tekrar (ALREADY_IN_STATE) ve çelişen
 * sonuç (CONFLICTING_FINAL) hiçbir şey değiştirmez. Loglarda id, tutar ya da hata kodu yok.
 */
@Service
public class PaymentResults {

	private static final Logger log = LoggerFactory.getLogger(PaymentResults.class);

	private final PaymentRepository payments;

	private final OutboxService outbox;

	private final PaymentEventFactory events;

	private final Clock clock;

	public PaymentResults(PaymentRepository payments, OutboxService outbox, PaymentEventFactory events, Clock clock) {
		this.payments = payments;
		this.outbox = outbox;
		this.events = events;
		this.clock = clock;
	}

	/** @throws ResourceNotFoundException ödeme yoksa */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public TransitionResult recordSucceeded(UUID paymentId) {
		Payment payment = lock(paymentId);
		TransitionResult result = payment.succeed(this.clock);
		if (result == TransitionResult.APPLIED) {
			this.outbox.append(PaymentEventFactory.PAYMENT_AGGREGATE, payment.getId(), PaymentSucceededEvent.TYPE,
					eventId -> this.events.succeeded(eventId, payment));
		}
		logConflict(result);
		return result;
	}

	/**
	 * @param failureCode {@code ^[A-Z][A-Z0-9_]{0,63}$}
	 * @throws ResourceNotFoundException ödeme yoksa
	 * @throws IllegalArgumentException kod biçimi geçersizse
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public TransitionResult recordFailed(UUID paymentId, String failureCode) {
		Payment payment = lock(paymentId);
		TransitionResult result = payment.fail(failureCode, this.clock);
		if (result == TransitionResult.APPLIED) {
			this.outbox.append(PaymentEventFactory.PAYMENT_AGGREGATE, payment.getId(), PaymentFailedEvent.TYPE,
					eventId -> this.events.failed(eventId, payment));
		}
		logConflict(result);
		return result;
	}

	private Payment lock(UUID paymentId) {
		return this.payments.findByIdForUpdate(paymentId)
			.orElseThrow(() -> new ResourceNotFoundException("Payment was not found."));
	}

	private static void logConflict(TransitionResult result) {
		if (result == TransitionResult.CONFLICTING_FINAL) {
			log.warn("Conflicting payment result ignored");
		}
	}

}
