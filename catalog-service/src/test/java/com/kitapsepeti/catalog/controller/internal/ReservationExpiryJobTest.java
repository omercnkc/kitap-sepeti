package com.kitapsepeti.catalog.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.kitapsepeti.catalog.service.ReservationExpiryJob;
import com.kitapsepeti.catalog.service.StockProperties;
import com.kitapsepeti.catalog.service.StockReservationTransactions;
import com.kitapsepeti.catalog.support.SqlCapture;
import com.kitapsepeti.catalog.support.StockInvariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Süre dolumu görevi. Test profilinde görev bean'i yoktur; burada gerçek transaction bean'iyle elle kurulur ve turlar
 * doğrudan çağrılır (zamanlayıcı beklenmez). Zaman {@link #clock} ile ilerletilir; TTL 15 dakika.
 */
@ExtendWith(OutputCaptureExtension.class)
class ReservationExpiryJobTest extends InternalStockTestSupport {

	private static final Duration PAST_TTL = Duration.ofMinutes(16);

	/** Görevin logger adı (sondaki boşluk test sınıfının kendi loglarını dışarıda bırakır). */
	private static final String JOB_LOGGER = ".ReservationExpiryJob ";

	@Autowired
	private StockReservationTransactions transactions;

	@Autowired
	private StockProperties properties;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private ReservationExpiryJob job;

	@BeforeEach
	void createJob() {
		job = jobWithBatchSize(100);
	}

	// --- 1. Süresi dolan rezervasyon serbest kalır

	@Test
	void expiredReservationIsReleasedAndInStockFlipEmitsBookUpserted() throws Exception {
		UUID lastCopies = book("published", 2);
		UUID plenty = book("published", 10);
		UUID orderId = UUID.randomUUID();
		reserve(request(orderId, lastCopies, 2, plenty, 1)).andExpect(status().isCreated());
		assertThat(outbox()).hasSize(1);
		clock.advance(PAST_TTL);

		ReservationExpiryJob.Result result = job.releaseExpired();

		assertThat(result).isEqualTo(new ReservationExpiryJob.Result(1, 2));
		assertThat(rowsOf(orderId)).extracting(row -> row.get("status")).containsOnly("released");
		assertThat(reservedOf(lastCopies)).isZero();
		assertThat(reservedOf(plenty)).isZero();
		assertThat(stockOf(lastCopies)).isEqualTo(2);
		List<Map<String, Object>> events = outbox();
		assertThat(events).hasSize(2);
		assertThat(events.get(1).get("event_type")).isEqualTo("BookUpserted");
		assertThat(events.get(1).get("aggregate_id")).isEqualTo(lastCopies.toString());
		assertThat(payload(events.get(1)).get("inStock")).isEqualTo(true);
		StockInvariant.assertHolds(jdbc);
	}

	@Test
	void roundEndsWithSingleInfoLineWhenSomethingWasReleased(CapturedOutput output) throws Exception {
		UUID book = book("published", 10);
		reserve(request(UUID.randomUUID(), book, 1)).andExpect(status().isCreated());
		reserve(request(UUID.randomUUID(), book, 2)).andExpect(status().isCreated());
		clock.advance(PAST_TTL);

		job.releaseExpired();
		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(0, 0));

		List<String> lines = jobLines(output);
		assertThat(lines).hasSize(1);
		assertThat(lines.getFirst()).contains("INFO").contains("Released expired reservations: orders=2, rows=2");
	}

	// --- 2. Süresi dolmamış, onaylanmış ve serbest bırakılmış satırlara dokunulmaz

	@Test
	void unexpiredCommittedAndReleasedReservationsAreUntouched() throws Exception {
		UUID book = book("published", 20);
		UUID expired = UUID.randomUUID();
		UUID committed = UUID.randomUUID();
		UUID released = UUID.randomUUID();
		reserve(request(expired, book, 1)).andExpect(status().isCreated());
		reserve(request(committed, book, 2)).andExpect(status().isCreated());
		reserve(request(released, book, 3)).andExpect(status().isCreated());
		commit(committed).andExpect(status().isOk());
		release(released).andExpect(status().isOk());
		clock.advance(Duration.ofMinutes(10));
		UUID fresh = UUID.randomUUID();
		reserve(request(fresh, book, 4)).andExpect(status().isCreated());
		clock.advance(Duration.ofMinutes(6));
		List<Map<String, Object>> committedRows = rowsOf(committed);
		List<Map<String, Object>> releasedRows = rowsOf(released);
		List<Map<String, Object>> freshRows = rowsOf(fresh);
		int eventsBefore = outbox().size();

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(1, 1));

		assertThat(rowsOf(expired)).extracting(row -> row.get("status")).containsExactly("released");
		assertThat(rowsOf(fresh)).isEqualTo(freshRows);
		assertThat(rowsOf(committed)).isEqualTo(committedRows);
		assertThat(rowsOf(released)).isEqualTo(releasedRows);
		assertThat(stockOf(book)).isEqualTo(18);
		assertThat(reservedOf(book)).isEqualTo(4);
		assertThat(outbox()).hasSize(eventsBefore);
		StockInvariant.assertHolds(jdbc);
	}

	@Test
	void reservationExpiringExactlyNowIsNotReleased() throws Exception {
		UUID book = book("published", 5);
		UUID orderId = UUID.randomUUID();
		reserve(request(orderId, book, 1)).andExpect(status().isCreated());
		clock.advance(Duration.ofMinutes(15));

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(0, 0));
		assertThat(rowsOf(orderId)).extracting(row -> row.get("status")).containsExactly("held");
	}

	// --- 3. Görevden sonra onay ve iptal

	@Test
	void afterExpiryCommitIsRejectedAndReleaseIsIdempotent() throws Exception {
		UUID book = book("published", 5);
		UUID orderId = UUID.randomUUID();
		reserve(request(orderId, book, 2)).andExpect(status().isCreated());
		clock.advance(PAST_TTL);
		job.releaseExpired();

		commit(orderId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_RELEASED"));
		release(orderId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("released"));
		fetch(orderId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("released"));
		assertThat(stockOf(book)).isEqualTo(5);
		assertThat(reservedOf(book)).isZero();
		StockInvariant.assertHolds(jdbc);
	}

	@Test
	void expiredButStillHeldReservationCanBeCommittedBeforeJobRuns() throws Exception {
		UUID book = book("published", 5);
		UUID orderId = UUID.randomUUID();
		reserve(request(orderId, book, 2)).andExpect(status().isCreated());
		clock.advance(PAST_TTL);

		commit(orderId).andExpect(status().isOk());

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(0, 0));
		assertThat(rowsOf(orderId)).extracting(row -> row.get("status")).containsExactly("committed");
		assertThat(stockOf(book)).isEqualTo(3);
		StockInvariant.assertHolds(jdbc);
	}

	// --- 4. Kilit yarışı: satırları başka transaction tutuyorsa sipariş atlanır

	@Test
	void orderLockedByAnotherTransactionIsSkippedAndReleasedNextRound(CapturedOutput output) throws Exception {
		UUID book = book("published", 5);
		UUID orderId = UUID.randomUUID();
		reserve(request(orderId, book, 1)).andExpect(status().isCreated());
		clock.advance(PAST_TTL);

		try (RowLock lock = holdRowLocks("SELECT id FROM stock_reservations WHERE order_id = UUID_TO_BIN(?) FOR UPDATE",
				orderId)) {
			long started = System.nanoTime();
			assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(0, 0));
			assertThat(Duration.ofNanos(System.nanoTime() - started)).as("SKIP LOCKED does not wait")
				.isLessThan(Duration.ofSeconds(5));
			assertThat(rowsOf(orderId)).extracting(row -> row.get("status")).containsExactly("held");
		}

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(1, 1));
		assertThat(reservedOf(book)).isZero();
		assertThat(jobLines(output)).noneMatch(line -> line.contains("WARN") || line.contains("ERROR"));
		StockInvariant.assertHolds(jdbc);
	}

	@Test
	void partiallyLockedOrderIsSkippedWholly() throws Exception {
		UUID a = book("published", 5);
		UUID b = book("published", 5);
		UUID orderId = UUID.randomUUID();
		reserve(request(orderId, a, 1, b, 1)).andExpect(status().isCreated());
		clock.advance(PAST_TTL);

		try (RowLock lock = holdRowLocks("SELECT id FROM stock_reservations WHERE order_id = UUID_TO_BIN(?) "
				+ "AND book_id = UUID_TO_BIN('" + b + "') FOR UPDATE", orderId)) {
			assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(0, 0));
			assertThat(rowsOf(orderId)).extracting(row -> row.get("status")).containsOnly("held");
			assertThat(reservedOf(a)).isEqualTo(1);
		}

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(1, 2));
		StockInvariant.assertHolds(jdbc);
	}

	@Test
	void lockQueryUsesSkipLocked() throws Exception {
		UUID book = book("published", 5);
		reserve(request(UUID.randomUUID(), book, 1)).andExpect(status().isCreated());
		clock.advance(PAST_TTL);

		SqlCapture.start();
		job.releaseExpired();
		List<String> statements = SqlCapture.stop();

		assertThat(statements).anySatisfy(sql -> assertThat(sql.toLowerCase())
			.contains("from stock_reservations")
			.contains("for update")
			.contains("skip locked"));
	}

	// --- 5. Tur başına en fazla batch-size sipariş, en eski önce

	@Test
	void batchSizeLimitsOrdersPerRoundOldestFirst() {
		UUID book = book("published", 200);
		List<UUID> orders = new ArrayList<>();
		for (int i = 0; i < 150; i++) {
			UUID orderId = UUID.randomUUID();
			orders.add(orderId);
			insertHeld(orderId, book, 1, NOW.plusSeconds(i));
		}
		jdbc.update("UPDATE books SET reserved_quantity = 150 WHERE id = UUID_TO_BIN(?)", book.toString());
		clock.advance(Duration.ofHours(1));

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(100, 100));
		assertThat(reservedOf(book)).isEqualTo(50);
		assertThat(heldOrders()).containsExactlyInAnyOrderElementsOf(orders.subList(100, 150));
		StockInvariant.assertHolds(jdbc);

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(50, 50));
		assertThat(reservedOf(book)).isZero();
		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(0, 0));
		StockInvariant.assertHolds(jdbc);
	}

	// --- 6. Hata izolasyonu

	@Test
	void failingOrderStaysHeldWithSingleWarnAndOthersAreReleased(CapturedOutput output) throws Exception {
		UUID broken = book("published", 5);
		UUID healthy = book("published", 5);
		UUID brokenOrder = UUID.randomUUID();
		UUID healthyOrder = UUID.randomUUID();
		reserve(request(brokenOrder, broken, 2)).andExpect(status().isCreated());
		clock.advance(Duration.ofSeconds(1));
		reserve(request(healthyOrder, healthy, 1)).andExpect(status().isCreated());
		jdbc.update("UPDATE books SET reserved_quantity = 1 WHERE id = UUID_TO_BIN(?)", broken.toString());
		clock.advance(PAST_TTL);

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(1, 1));

		assertThat(rowsOf(brokenOrder)).extracting(row -> row.get("status")).containsExactly("held");
		assertThat(reservedOf(broken)).as("rolled back").isEqualTo(1);
		assertThat(rowsOf(healthyOrder)).extracting(row -> row.get("status")).containsExactly("released");
		List<String> warnings = jobLines(output).stream().filter(line -> line.contains("WARN")).toList();
		assertThat(warnings).hasSize(1);
		assertThat(warnings.getFirst()).contains("orderId=" + brokenOrder).contains("error=IllegalStateException");
		assertThat(output).doesNotContain("\tat ").doesNotContain("IllegalStateException:").doesNotContain("ERROR");

		jdbc.update("UPDATE books SET reserved_quantity = 2 WHERE id = UUID_TO_BIN(?)", broken.toString());
		StockInvariant.assertHolds(jdbc);
	}

	// --- 8. Eşzamanlı onay ve görev: her turda tam biri kazanır

	@Test
	void concurrentCommitAndExpiryNeverBothWin() throws Exception {
		UUID book = book("published", 100);
		int committed = 0;
		for (int round = 0; round < 20; round++) {
			clock.fixAt(NOW);
			UUID orderId = UUID.randomUUID();
			reserve(request(orderId, book, 1)).andExpect(status().isCreated());
			clock.advance(PAST_TTL);

			List<Callable<Object>> tasks = List.of(
					() -> commit(orderId).andReturn().getResponse(),
					() -> job.releaseExpired());
			List<Object> outcomes = runConcurrently(tasks);
			MockHttpServletResponse commitResponse = (MockHttpServletResponse) outcomes.get(0);
			ReservationExpiryJob.Result expiry = (ReservationExpiryJob.Result) outcomes.get(1);

			boolean commitWon = commitResponse.getStatus() == 200;
			boolean expiryWon = expiry.orders() == 1;
			assertThat(commitWon).as("round %d: exactly one wins", round).isNotEqualTo(expiryWon);
			if (commitWon) {
				committed++;
				assertThat(rowsOf(orderId)).extracting(row -> row.get("status")).containsExactly("committed");
			}
			else {
				assertThat(commitResponse.getStatus()).isEqualTo(409);
				assertThat(commitResponse.getContentAsString()).contains("RESERVATION_RELEASED");
				assertThat(rowsOf(orderId)).extracting(row -> row.get("status")).containsExactly("released");
			}
			assertThat(stockOf(book)).isNotNegative();
			StockInvariant.assertHolds(jdbc);
		}
		assertThat(stockOf(book)).isEqualTo(100 - committed);
		assertThat(reservedOf(book)).isZero();
	}

	private ReservationExpiryJob jobWithBatchSize(int batchSize) {
		StockProperties.Expiry expiry = new StockProperties.Expiry(true, Duration.ofSeconds(30), batchSize);
		return new ReservationExpiryJob(transactions, new StockProperties(properties.reservationTtl(), expiry), clock);
	}

	private void insertHeld(UUID orderId, UUID bookId, int quantity, Instant expiresAt) {
		jdbc.update("INSERT INTO stock_reservations (id, book_id, order_id, quantity, status, expires_at) "
				+ "VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, 'held', ?)", UUID.randomUUID().toString(),
				bookId.toString(), orderId.toString(), quantity, LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
	}

	private List<UUID> heldOrders() {
		return jdbc.queryForList("SELECT BIN_TO_UUID(order_id) FROM stock_reservations WHERE status = 'held'",
				String.class).stream().map(UUID::fromString).toList();
	}

	private static List<String> jobLines(CapturedOutput output) {
		return output.getAll().lines().filter(line -> line.contains(JOB_LOGGER)).toList();
	}

	/**
	 * Siparişin satırlarını ayrı bir thread'deki transaction'da {@code sql} ile kilitler ve kapatılana kadar tutar
	 * (eşzamanlı bir onay/iptal gibi).
	 */
	private RowLock holdRowLocks(String sql, UUID orderId) throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch finish = new CountDownLatch(1);
		ExecutorService thread = Executors.newSingleThreadExecutor();
		Future<?> holder = thread.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			assertThat(jdbc.queryForList(sql, orderId.toString())).isNotEmpty();
			locked.countDown();
			try {
				finish.await(30, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}));
		assertThat(locked.await(10, TimeUnit.SECONDS)).as("row lock acquired").isTrue();
		return () -> {
			finish.countDown();
			holder.get(10, TimeUnit.SECONDS);
			thread.shutdownNow();
		};
	}

	private interface RowLock extends AutoCloseable {

		@Override
		void close() throws Exception;

	}

}
