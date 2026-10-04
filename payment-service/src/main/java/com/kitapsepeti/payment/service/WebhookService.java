package com.kitapsepeti.payment.service;

import java.time.Clock;

import com.kitapsepeti.payment.dto.webhook.WebhookEvent;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.entity.ProviderEvent;
import com.kitapsepeti.payment.entity.ProviderEventType;
import com.kitapsepeti.payment.entity.TransitionResult;
import com.kitapsepeti.payment.exception.PaymentErrorCode;
import com.kitapsepeti.payment.exception.WebhookRejectedException;
import com.kitapsepeti.payment.repository.PaymentRepository;
import com.kitapsepeti.payment.repository.ProviderEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * İmzası doğrulanmış webhook olayını işler; olay kaydı, durum geçişi ve outbox olayı TEK transaction'dadır.
 * <p>
 * Sıra: ödeme (sağlayıcı + referans) {@code FOR UPDATE} → olay daha önce işlendiyse hiçbir şey yapılmaz → tutar ve
 * para birimi ödemeyle aynı olmalı → olay kaydı ({@code provider_events}) → {@link PaymentResults} (aynı
 * transaction'a katılır). Ödeme kilidi aynı ödemeye gelen eşzamanlı webhook'ları sıraya sokar: aynı olayın ikinci
 * teslimi birincinin commit ettiği kaydı görür. Reddedilen olay (bilinmeyen ödeme, tutar uyuşmazlığı) kaydedilmez,
 * böylece düzeltilmiş tekrar teslim yeniden işlenebilir.
 */
@Service
public class WebhookService {

	/** Kilit sırasını aşan tekrar (ör. aynı olay kimliği farklı referansla) bu kısıtta düşer; çağıran tekrar sayar. */
	public static final String EVENT_CONSTRAINT = "uk_provider_events_provider_event";

	private final PaymentRepository payments;

	private final ProviderEventRepository providerEvents;

	private final PaymentResults results;

	private final Clock clock;

	public WebhookService(PaymentRepository payments, ProviderEventRepository providerEvents, PaymentResults results,
			Clock clock) {
		this.payments = payments;
		this.providerEvents = providerEvents;
		this.results = results;
		this.clock = clock;
	}

	/** @return olayın sonucu; daha önce işlenmiş olayda {@link Outcome#DUPLICATE} */
	public enum Outcome {

		APPLIED, ALREADY_IN_STATE, CONFLICTING_FINAL, DUPLICATE;

		static Outcome of(TransitionResult result) {
			return Outcome.valueOf(result.name());
		}

	}

	/**
	 * @throws WebhookRejectedException {@code UNKNOWN_PAYMENT} ya da {@code AMOUNT_MISMATCH} (hiçbir şey yazılmaz)
	 * @throws org.springframework.dao.DataIntegrityViolationException olay eşzamanlı olarak başka bir transaction'da
	 * kaydedildiyse ({@link #EVENT_CONSTRAINT}; bu transaction geri alınır)
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Outcome handle(PaymentProviderType provider, WebhookEvent event) {
		Payment payment = this.payments.findByProviderReferenceForUpdate(provider, event.providerPaymentId())
			.orElseThrow(() -> new WebhookRejectedException(PaymentErrorCode.UNKNOWN_PAYMENT));
		if (this.providerEvents.existsByProviderTypeAndProviderEventId(provider, event.eventId())) {
			return Outcome.DUPLICATE;
		}
		if (payment.getAmount().compareTo(event.amountValue()) != 0 || !payment.getCurrency().equals(event.currency())) {
			throw new WebhookRejectedException(PaymentErrorCode.AMOUNT_MISMATCH);
		}
		ProviderEventType type = event.eventType();
		this.providerEvents.saveAndFlush(
				ProviderEvent.record(provider, event.eventId(), payment.getId(), type, this.clock));
		TransitionResult result = (type == ProviderEventType.PAYMENT_SUCCEEDED)
				? this.results.recordSucceeded(payment.getId())
				: this.results.recordFailed(payment.getId(), event.failureCode());
		return Outcome.of(result);
	}

}
