package com.kitapsepeti.payment.outbox;

import java.time.Clock;
import java.util.List;

import com.kitapsepeti.payment.entity.OutboxEvent;
import com.kitapsepeti.payment.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox worker: yayınlanmamış satırları sırayla broker'a gönderir ve {@code published_at}'i doldurur.
 * Bir tur tek transaction'dır; satırlar {@code FOR UPDATE SKIP LOCKED} ile kilitlendiği için birden çok
 * instance aynı satırı aynı anda yayınlamaz. İlk hatada tur durur: o ana kadar yayınlananlar commit
 * edilir, hatalı satır ve sonrakiler sıra bozulmasın diye sonraki tura kalır.
 * Teslim garantisi at-least-once: onay alınıp commit'ten önce çökülürse mesaj tekrar gönderilir.
 * Loglarda payload yok; yalnızca olay id'si (= mesajın {@code message_id}'si) ve olay tipi.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	private final OutboxRepository outboxRepository;

	private final OutboxPublisher publisher;

	private final OutboxProperties properties;

	private final TransactionTemplate transactionTemplate;

	private final Clock clock;

	public OutboxRelay(OutboxRepository outboxRepository, OutboxPublisher publisher, OutboxProperties properties,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.outboxRepository = outboxRepository;
		this.publisher = publisher;
		this.properties = properties;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/**
	 * DB'ye ulaşılamıyorsa tur atlanır ve tek satır WARN yazılır; kesinti boyunca her turda stack trace basılmaz.
	 * Diğer exception'lar Spring'in scheduler error handler'ına gider (ERROR + stack trace).
	 */
	@Scheduled(fixedDelayString = "${app.outbox.poll-interval}")
	public void poll() {
		Integer published;
		try {
			published = transactionTemplate.execute(status -> relayBatch());
		}
		catch (DataAccessException | TransactionException ex) {
			log.warn("Outbox poll skipped, database unavailable: {}", ex.getClass().getSimpleName());
			return;
		}
		if (published != null && published > 0) {
			log.debug("Published {} outbox event(s)", published);
		}
	}

	/** Yayıncı hatası yakalanır ve transaction'ı geri aldırmaz; başarılı satırların güncellemesi commit edilir. */
	private int relayBatch() {
		List<OutboxEvent> batch = outboxRepository.lockUnpublishedBatch(properties.batchSize());
		int published = 0;
		for (OutboxEvent event : batch) {
			try {
				publisher.publish(event);
			}
			catch (RuntimeException ex) {
				log.warn("Outbox publish failed, will retry on next poll: id={}, eventType={}, error={}",
						event.getId(), event.getEventType(), describe(ex));
				break;
			}
			event.setPublishedAt(clock.instant());
			published++;
		}
		return published;
	}

	/** Mesaj metinleri OutboxPublisher'da sabittir (payload içermez); altta yatan sebep yalnızca sınıf adıyla. */
	private static String describe(RuntimeException ex) {
		Throwable cause = ex.getCause();
		return (cause == null) ? ex.getClass().getSimpleName() + ": " + ex.getMessage()
				: ex.getClass().getSimpleName() + ": " + ex.getMessage() + " (" + cause.getClass().getSimpleName() + ")";
	}

}
