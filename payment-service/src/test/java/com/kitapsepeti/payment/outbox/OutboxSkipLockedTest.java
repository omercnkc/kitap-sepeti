package com.kitapsepeti.payment.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.kitapsepeti.payment.ApiTestSupport;
import com.kitapsepeti.payment.entity.OutboxEvent;
import com.kitapsepeti.payment.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Batch sorgusunun kilit davranışı. Worker kapalı context'te koşar (paylaşılan context); açık olsaydı
 * worker da aynı satırları kilitleyip sonucu belirsizleştirirdi.
 */
class OutboxSkipLockedTest extends ApiTestSupport {

	@Autowired
	private OutboxRepository outboxRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void concurrentTransactionsLockDisjointBatchesWithoutWaiting() throws Exception {
		List<UUID> ids = OutboxTestRows.newIds(4);
		OutboxTestRows.insert(jdbc, ids);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		CountDownLatch firstLocked = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<List<UUID>> first = executor.submit(() -> tx.execute(status -> {
				List<UUID> locked = idsOf(outboxRepository.lockUnpublishedBatch(2));
				firstLocked.countDown();
				awaitQuietly(releaseFirst);
				return locked;
			}));
			assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();

			// Birinci transaction kilitlerini tutarken: SKIP LOCKED olmasa bu sorgu lock wait'te beklerdi.
			Future<List<UUID>> second = executor.submit(
					() -> tx.execute(status -> idsOf(outboxRepository.lockUnpublishedBatch(2))));
			List<UUID> secondBatch = second.get(5, TimeUnit.SECONDS);

			releaseFirst.countDown();
			List<UUID> firstBatch = first.get(10, TimeUnit.SECONDS);

			assertThat(firstBatch).containsExactly(ids.get(0), ids.get(1));
			assertThat(secondBatch).containsExactly(ids.get(2), ids.get(3));
		}
		finally {
			releaseFirst.countDown();
			executor.shutdownNow();
		}
	}

	private static List<UUID> idsOf(List<OutboxEvent> events) {
		return events.stream().map(OutboxEvent::getId).toList();
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
