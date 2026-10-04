package com.kitapsepeti.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.kitapsepeti.order.TestcontainersConfiguration;
import com.kitapsepeti.order.entity.AddressSnapshot;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link OrderRepository#findByIdForUpdate} kilidinin iki gerçek transaction arasındaki davranışı. Test metodu
 * transaction'sız koşar (her thread kendi transaction'ını açar); veri commit edildiği için kendi satırları silinir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderLockingTest {

	private static final long HOLD_MILLIS = 1_000;

	/** application.yml {@code connection-init-sql}: {@code innodb_lock_wait_timeout = 5} (saniye). */
	private static final long LOCK_TIMEOUT_MILLIS = 5_000;

	@Autowired
	private OrderRepository orders;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbc;

	private TransactionTemplate tx;

	private ExecutorService pool;

	private UUID orderId;

	@BeforeEach
	void setUp() {
		tx = new TransactionTemplate(transactionManager);
		pool = Executors.newFixedThreadPool(2);
		AddressSnapshot address = new AddressSnapshot("Alıcı", "+905550000000", "Sokak 1", null, null, "Ankara", null,
				"TR");
		orderId = tx.execute(status -> orders.save(Order.place(UUID.randomUUID(), UUID.randomUUID(), "TRY",
				List.of(new OrderLine(UUID.randomUUID(), "Kitap", 1, BigDecimal.TEN)), address, Clock.systemUTC()))
			.getId());
	}

	@AfterEach
	void cleanUp() {
		pool.shutdownNow();
		byte[] id = bytes(orderId);
		jdbc.update("DELETE FROM order_status_history WHERE order_id = ?", (Object) id);
		jdbc.update("DELETE FROM order_items WHERE order_id = ?", (Object) id);
		jdbc.update("DELETE FROM orders WHERE id = ?", (Object) id);
	}

	@Test
	void secondLockWaitsUntilFirstTransactionCommits() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
			orders.findByIdForUpdate(orderId).orElseThrow();
			locked.countDown();
			await(release);
		}));
		assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

		Future<Long> waiter = pool.submit(() -> tx.execute(status -> {
			long start = System.nanoTime();
			orders.findByIdForUpdate(orderId).orElseThrow();
			return Duration.ofNanos(System.nanoTime() - start).toMillis();
		}));
		Thread.sleep(HOLD_MILLIS);
		assertThat(waiter.isDone()).as("second transaction blocked while first holds the lock").isFalse();
		release.countDown();
		holder.get(10, TimeUnit.SECONDS);
		long waitedMillis = waiter.get(10, TimeUnit.SECONDS);

		assertThat(waitedMillis).isGreaterThanOrEqualTo(HOLD_MILLIS / 2).isLessThan(LOCK_TIMEOUT_MILLIS);
	}

	@Test
	void lockWaitGivesUpAfterLockTimeout() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
			orders.findByIdForUpdate(orderId).orElseThrow();
			locked.countDown();
			await(release);
		}));
		assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

		AtomicLong start = new AtomicLong();
		Throwable thrown = catchThrowable(() -> tx.executeWithoutResult(status -> {
			start.set(System.nanoTime());
			orders.findByIdForUpdate(orderId);
		}));
		long waitedMillis = Duration.ofNanos(System.nanoTime() - start.get()).toMillis();
		release.countDown();
		holder.get(10, TimeUnit.SECONDS);

		assertThat(thrown).isInstanceOf(PessimisticLockingFailureException.class);
		assertThat(waitedMillis).isBetween(LOCK_TIMEOUT_MILLIS - 500, LOCK_TIMEOUT_MILLIS + 3_000);
	}

	/** Kilitsiz okuma (findById) kilidi beklemez: yalnızca güncelleme yolları sıraya girer. */
	@Test
	void plainReadDoesNotWaitForTheLock() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
			orders.findByIdForUpdate(orderId).orElseThrow();
			locked.countDown();
			await(release);
		}));
		assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

		long start = System.nanoTime();
		boolean found = Boolean.TRUE.equals(tx.execute(status -> orders.findById(orderId).isPresent()));
		long waitedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
		release.countDown();
		holder.get(10, TimeUnit.SECONDS);

		assertThat(found).isTrue();
		assertThat(waitedMillis).isLessThan(HOLD_MILLIS);
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16)
			.putLong(id.getMostSignificantBits())
			.putLong(id.getLeastSignificantBits())
			.array();
	}

}
