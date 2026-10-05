package com.kitapsepeti.order.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kitapsepeti.order.entity.OrderStatus;
import com.kitapsepeti.order.repository.OrderRepository;
import com.kitapsepeti.order.repository.StockSyncCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Catalog stok senkronizasyon kurtarma görevi.
 * <p>
 * Dispatcher'da atlanan veya henüz kesinleştirilmemiş/serbest bırakılmamış siparişleri periyodik olarak toplar:
 * <ul>
 * <li>paid + held &rarr; commit (başarılıysa committed, bırakılmışsa lost + ERROR STOCK_COMMIT_LOST)</li>
 * <li>failed + requested/held &rarr; release &rarr; released</li>
 * </ul>
 * <p>
 * Tek instance varsayımı geçerlidir. Seçim kilitsizdir, Catalog çağrısı transaction dışındadır;
 * kilit ve geçiş {@link StockCoordinator} içinde FOR UPDATE ile yapılır.
 * Loglar sipariş id'si veya tutar içermez.
 */
@Component
@ConditionalOnProperty(name = "app.stock-sync.enabled", havingValue = "true", matchIfMissing = true)
public class StockSyncJob {

	private static final Logger log = LoggerFactory.getLogger(StockSyncJob.class);

	private final OrderRepository orders;

	private final StockCoordinator coordinator;

	private final Clock clock;

	private final Duration minAge;

	private final int batchSize;

	public StockSyncJob(OrderRepository orders, StockCoordinator coordinator, Clock clock,
			@Value("${app.stock-sync.min-age:10s}") Duration minAge,
			@Value("${app.stock-sync.batch:50}") int batchSize) {
		this.orders = orders;
		this.coordinator = coordinator;
		this.clock = clock;
		this.minAge = minAge;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${app.stock-sync.interval:30s}",
			initialDelayString = "${app.stock-sync.initial-delay:10s}")
	public void run() {
		executeRound();
	}

	/**
	 * Tek bir kurtarma turunu çalıştırır. Testlerden doğrudan çağrılabilir.
	 *
	 * @return işlenen aday sayısı
	 */
	public int executeRound() {
		Instant cutoff = this.clock.instant().minus(this.minAge);
		List<StockSyncCandidate> candidates = this.orders.findStockSyncCandidates(cutoff,
				PageRequest.of(0, this.batchSize));
		if (candidates.isEmpty()) {
			return 0;
		}

		Map<StockOutcome, Integer> counts = new EnumMap<>(StockOutcome.class);
		int processed = 0;

		for (StockSyncCandidate candidate : candidates) {
			StockOutcome outcome;
			if (candidate.status() == OrderStatus.PAID) {
				outcome = this.coordinator.processCommit(candidate.id());
			}
			else {
				outcome = this.coordinator.processRelease(candidate.id());
			}
			counts.merge(outcome, 1, Integer::sum);
			processed++;

			if (outcome == StockOutcome.NOT_PERFORMED) {
				log.warn("Catalog service unavailable or circuit breaker open; terminating stock sync round early");
				break;
			}
		}

		if (processed > 0) {
			String summary = counts.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByKey())
				.map(e -> e.getKey().name().toLowerCase(java.util.Locale.ROOT) + "=" + e.getValue())
				.collect(Collectors.joining(", "));
			log.info("Stock sync round completed: processed={}, counts=[{}]", processed, summary);
		}

		return processed;
	}

}
