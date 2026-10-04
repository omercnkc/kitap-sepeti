package com.kitapsepeti.payment.provider.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.payment.config.PaymentProperties;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.entity.PaymentStatus;
import com.kitapsepeti.payment.provider.mock.MockWebhookDispatcher.Delivery;
import com.kitapsepeti.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Webhook'u ulaşmamış mock ödemeleri toparlar: otomatik gönderim düşürüldüyse, webhook ucu 5xx / zaman aşımı
 * döndüyse ya da uygulama gönderimden önce kapandıysa ödeme {@code initiated} kalır; bu görev aynı webhook'u
 * {@link MockWebhookDispatcher#send} ile yeniden gönderir.
 * <p>
 * Aday: {@code initiated}, mock, referanslı ve {@code app.payment.mock.recovery.min-age}'den eski (daha yenisinin
 * normal gönderimi sürüyor olabilir); {@code created_at} sırasıyla en fazla {@code batch-size} tane
 * ({@code ix_payments_status_created}). Referanssız {@code initiated} ödemeye (sağlayıcı çağrısı başarısız)
 * dokunulmaz: sağlayıcıda ödeme yok, Order'ın tekrar denemesi tamamlar. Ödemeler sırayla, senkron gönderilir; birinin
 * hatası diğerlerini durdurmaz ({@code send} exception atmaz).
 * <p>
 * Birden çok instance (ya da otomatik gönderimle yarışan bir tur) aynı ödemeyi aynı anda gönderebilir; kilit
 * bilinçli olarak yok. Olay kimliği ödeme başına sabit ({@code mock_evt_<paymentId>}) olduğundan webhook ucu
 * ikinci teslimi tekrar olarak 204 ile yok sayar; olay kaydı ve outbox satırı bir kez yazılır.
 * <p>
 * Log yalnızca sayıdır: ödeme id'si, referans ve tutar yazılmaz.
 */
public class MockRecoveryJob {

	private static final Logger log = LoggerFactory.getLogger(MockRecoveryJob.class);

	private final PaymentRepository payments;

	private final MockWebhookDispatcher dispatcher;

	private final Clock clock;

	private final Duration minAge;

	private final int batchSize;

	public MockRecoveryJob(PaymentRepository payments, MockWebhookDispatcher dispatcher, Clock clock,
			PaymentProperties.Recovery settings) {
		this.payments = payments;
		this.dispatcher = dispatcher;
		this.clock = clock;
		this.minAge = settings.minAge();
		this.batchSize = settings.batchSize();
	}

	/** İlk tur da bir aralık sonra: uygulama açılırken webhook adresi (port) henüz bilinmiyor olabilir. */
	@Scheduled(fixedDelayString = "${app.payment.mock.recovery.interval}",
			initialDelayString = "${app.payment.mock.recovery.interval}")
	public void run() {
		resendStale();
	}

	/**
	 * Bir tur. DB'ye ulaşılamıyorsa tur atlanır ve tek satır WARN yazılır.
	 *
	 * @return webhook ucunun kabul ettiği (2xx) gönderim sayısı
	 */
	public int resendStale() {
		Instant cutoff = this.clock.instant().minus(this.minAge);
		List<UUID> candidates;
		try {
			candidates = this.payments.findStaleWithReference(PaymentStatus.INITIATED, PaymentProviderType.MOCK,
					cutoff, Limit.of(this.batchSize));
		}
		catch (DataAccessException ex) {
			log.warn("Mock recovery skipped, database unavailable: {}", ex.getClass().getSimpleName());
			return 0;
		}
		int resent = 0;
		for (UUID paymentId : candidates) {
			if (this.dispatcher.send(paymentId) == Delivery.DELIVERED) {
				resent++;
			}
		}
		if (resent > 0) {
			log.info("Mock recovery resent {} webhook(s)", resent);
		}
		return resent;
	}

}
