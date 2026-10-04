package com.kitapsepeti.payment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.common.error.ResourceNotFoundException;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.exception.PaymentOrderMismatchException;
import com.kitapsepeti.payment.provider.PaymentProvider;
import com.kitapsepeti.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ödemenin DB transaction'ları ({@link PaymentService} bunları sağlayıcı çağrısından önce ve sonra, ayrı ayrı çağırır;
 * sağlayıcı hiçbir zaman transaction içinde çağrılmaz).
 * <p>
 * Yazma transaction'ları READ COMMITTED (cart ile aynı gerekçe): aynı sipariş için eşzamanlı iki ilk istekte ikinci
 * INSERT {@code uk_payments_order}'da birincinin commit'ini bekleyip ihlalle düşer, servis yeni transaction'da bir kez
 * yeniden dener ve bu kez var olan ödemeyi okur.
 * <p>
 * Loglar ödeme/sipariş/kullanıcı id'si, tutar ve sağlayıcı referansı içermez.
 */
@Component
public class PaymentTransactions {

	private static final Logger log = LoggerFactory.getLogger(PaymentTransactions.class);

	private final PaymentRepository payments;

	private final PaymentProvider provider;

	private final Clock clock;

	public PaymentTransactions(PaymentRepository payments, PaymentProvider provider, Clock clock) {
		this.payments = payments;
		this.provider = provider;
		this.clock = clock;
	}

	/** @param created ödeme bu çağrıda mı oluşturuldu (yanıt 201 / 200) */
	public record Initiation(Payment payment, boolean created) {
	}

	/**
	 * Siparişin ödemesi; yoksa {@code initiated} olarak oluşturur (sağlayıcı referansı henüz yok).
	 * @throws PaymentOrderMismatchException ödeme var ama kullanıcı, tutar ya da para birimi farklı (ödeme değişmez)
	 * @throws org.springframework.dao.DataIntegrityViolationException eşzamanlı ilk istek ({@code uk_payments_order})
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Initiation findOrCreate(UUID orderId, UUID userId, BigDecimal amount, String currency) {
		Optional<Payment> existing = this.payments.findByOrderId(orderId);
		if (existing.isPresent()) {
			Payment payment = existing.get();
			if (!payment.matches(userId, amount, currency)) {
				throw new PaymentOrderMismatchException();
			}
			return new Initiation(payment, false);
		}
		// Hemen flush: eşzamanlı ilk istek ihlali burada, transaction içinde görülsün.
		Payment created = this.payments.saveAndFlush(
				Payment.initiate(orderId, userId, amount, currency, this.provider.type(), this.clock));
		return new Initiation(created, true);
	}

	/**
	 * Sağlayıcının döndürdüğü referansı ödemeye yazar; satır {@code FOR UPDATE} ile okunur, eşzamanlı yazımlar sıraya
	 * girer. Referans boşsa yazılır; aynıysa hiçbir şey değişmez. Başka bir referans zaten yazılmışsa (aynı ödeme için
	 * eşzamanlı ikinci sağlayıcı çağrısı) mevcut korunur, yeni referans atılır ve WARN yazılır. Ödeme bu arada
	 * sonuçlanmışsa da dokunulmaz.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Payment attachReference(UUID paymentId, String providerPaymentId) {
		Payment payment = this.payments.findByIdForUpdate(paymentId)
			.orElseThrow(() -> new IllegalStateException("Payment to attach a provider reference to does not exist"));
		String current = payment.getProviderPaymentId();
		if (current != null && !current.equals(providerPaymentId)) {
			log.warn("Payment already has another provider reference; keeping it and discarding the new one");
			return payment;
		}
		if (current == null && payment.getStatus().isFinal()) {
			return payment;
		}
		if (payment.attachProviderReference(providerPaymentId, this.clock)) {
			this.payments.flush();
		}
		return payment;
	}

	/** @throws ResourceNotFoundException ödeme yoksa (yanıtta id yok) */
	@Transactional(readOnly = true)
	public Payment find(UUID paymentId) {
		return this.payments.findById(paymentId)
			.orElseThrow(() -> new ResourceNotFoundException("Payment was not found."));
	}

}
