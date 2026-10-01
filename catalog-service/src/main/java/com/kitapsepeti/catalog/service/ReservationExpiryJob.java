package com.kitapsepeti.catalog.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

/**
 * Süresi dolan 'held' rezervasyonları serbest bırakır. Aday siparişler kilitsiz okunur; her sipariş AYRI bir
 * transaction'da {@link StockReservationTransactions#releaseExpired} ile bırakılır (iptal ucuyla aynı kod, aynı
 * kilit sırası, aynı olay kuralı). O an onaylanan/iptal edilen sipariş beklenmeden atlanır ve sonraki turda
 * yeniden değerlendirilir. Yeni olay türü yoktur; inStock değişirse {@code BookUpserted} yazılır.
 */
@Component
@ConditionalOnProperty(name = "app.stock.expiry.enabled", havingValue = "true")
public class ReservationExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

	private final StockReservationTransactions transactions;

	private final StockProperties properties;

	private final Clock clock;

	public ReservationExpiryJob(StockReservationTransactions transactions, StockProperties properties, Clock clock) {
		this.transactions = transactions;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.stock.expiry.interval}")
	public void run() {
		releaseExpired();
	}

	/**
	 * Tek tur. Bir siparişteki hata yalnızca o siparişi geri aldırır (tek satır WARN, stack trace yok) ve tur sürer.
	 * DB'ye ulaşılamıyorsa (aday okuması başarısız ya da transaction açılamıyor / bağlantı koptu) tek satır WARN ile
	 * tur biter.
	 */
	public Result releaseExpired() {
		Instant now = this.clock.instant();
		List<UUID> candidates;
		try {
			candidates = this.transactions.findExpiredOrderIds(now, this.properties.expiry().batchSize());
		}
		catch (DataAccessException | TransactionException ex) {
			logDatabaseUnavailable(ex);
			return Result.NONE;
		}

		int orders = 0;
		int rows = 0;
		for (UUID orderId : candidates) {
			int released;
			try {
				released = this.transactions.releaseExpired(orderId, now);
			}
			catch (DataAccessResourceFailureException | TransactionException ex) {
				logDatabaseUnavailable(ex);
				break;
			}
			catch (RuntimeException ex) {
				log.warn("Reservation expiry failed, order stays held until next run: orderId={}, error={}", orderId,
						ex.getClass().getSimpleName());
				continue;
			}
			if (released > 0) {
				orders++;
				rows += released;
			}
		}
		if (orders > 0) {
			log.info("Released expired reservations: orders={}, rows={}", orders, rows);
		}
		return new Result(orders, rows);
	}

	private static void logDatabaseUnavailable(RuntimeException ex) {
		log.warn("Reservation expiry skipped, database unavailable: {}", ex.getClass().getSimpleName());
	}

	/** Bir turun sonucu: serbest bırakılan sipariş ve satır sayısı. */
	public record Result(int orders, int rows) {

		static final Result NONE = new Result(0, 0);

	}

}
