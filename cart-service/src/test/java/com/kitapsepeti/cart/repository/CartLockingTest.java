package com.kitapsepeti.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.kitapsepeti.cart.TestcontainersConfiguration;
import com.kitapsepeti.cart.entity.Cart;
import com.kitapsepeti.cart.entity.CartStatus;
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
 * {@link CartRepository#findActiveByUserIdForUpdate} kilidinin iki gerçek transaction arasındaki davranışı.
 * Test metodu transaction'sız koşar (her thread kendi transaction'ını açar); veri commit edildiği için temizlenir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CartLockingTest {

	private static final long HOLD_MILLIS = 1_000;

	/** application.yml {@code connection-init-sql}: {@code innodb_lock_wait_timeout = 5} (saniye). */
	private static final long LOCK_TIMEOUT_MILLIS = 5_000;

	@Autowired
	private CartRepository carts;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbc;

	private TransactionTemplate tx;

	private ExecutorService pool;

	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		tx = new TransactionTemplate(transactionManager);
		pool = Executors.newFixedThreadPool(2);
		tx.executeWithoutResult(status -> carts.save(Cart.openFor(userId, Clock.systemUTC())));
	}

	@AfterEach
	void cleanUp() {
		pool.shutdownNow();
		jdbc.update("DELETE FROM carts");
	}

	@Test
	void secondLockWaitsUntilFirstTransactionCommits() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
			carts.findActiveByUserIdForUpdate(userId).orElseThrow();
			locked.countDown();
			await(release);
		}));
		assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

		Future<Long> waiter = pool.submit(() -> tx.execute(status -> {
			long start = System.nanoTime();
			carts.findActiveByUserIdForUpdate(userId).orElseThrow();
			return Duration.ofNanos(System.nanoTime() - start).toMillis();
		}));
		Thread.sleep(HOLD_MILLIS);
		assertThat(waiter.isDone()).as("second transaction blocked while first holds the lock").isFalse();
		release.countDown();
		holder.get(10, TimeUnit.SECONDS);
		long waitedMillis = waiter.get(10, TimeUnit.SECONDS);

		assertThat(waitedMillis).isGreaterThanOrEqualTo(HOLD_MILLIS / 2).isLessThan(LOCK_TIMEOUT_MILLIS);
	}

	/**
	 * CartCheckedOut tüketicisi (id ile kilit) ile kullanıcının eklemesi (kullanıcı + active ile kilit) aynı satırda
	 * sıraya girer; kapanış commit edilince bekleyen ekleme aktif sepet bulamaz (READ COMMITTED: kilitli okuma son
	 * commit'i görür) ve servis yeni sepet açar.
	 */
	@Test
	void checkoutByIdBlocksActiveLockAndWaiterThenSeesNoActiveCart() throws Exception {
		UUID cartId = carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
			.orElseThrow()
			.getId();
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		TransactionTemplate readCommitted = new TransactionTemplate(transactionManager);
		readCommitted.setIsolationLevel(TransactionTemplate.ISOLATION_READ_COMMITTED);
		Future<?> holder = pool.submit(() -> readCommitted.executeWithoutResult(status -> {
			Cart cart = carts.findByIdForUpdate(cartId).orElseThrow();
			locked.countDown();
			await(release);
			cart.checkout(Clock.systemUTC());
			carts.flush();
		}));
		assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

		Future<Boolean> waiter = pool.submit(
				() -> readCommitted.execute(status -> carts.findActiveByUserIdForUpdate(userId).isPresent()));
		Thread.sleep(HOLD_MILLIS);
		assertThat(waiter.isDone()).as("user lock waits for the checkout lock").isFalse();
		release.countDown();
		holder.get(10, TimeUnit.SECONDS);

		assertThat(waiter.get(10, TimeUnit.SECONDS)).isFalse();
	}

	@Test
	void lockWaitGivesUpAfterLockTimeout() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
			carts.findActiveByUserIdForUpdate(userId).orElseThrow();
			locked.countDown();
			await(release);
		}));
		assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

		AtomicLong start = new AtomicLong();
		Throwable thrown = catchThrowable(() -> tx.executeWithoutResult(status -> {
			start.set(System.nanoTime());
			carts.findActiveByUserIdForUpdate(userId);
		}));
		long waitedMillis = Duration.ofNanos(System.nanoTime() - start.get()).toMillis();
		release.countDown();
		holder.get(10, TimeUnit.SECONDS);

		assertThat(thrown).isInstanceOf(PessimisticLockingFailureException.class);
		assertThat(waitedMillis).isBetween(LOCK_TIMEOUT_MILLIS - 500, LOCK_TIMEOUT_MILLIS + 3_000);
	}

	@Test
	void poolConnectionsUseFiveSecondLockWaitTimeout() {
		Long global = jdbc.queryForObject("SELECT @@GLOBAL.innodb_lock_wait_timeout", Long.class);
		Long session = tx.execute(status -> {
			carts.findActiveByUserIdForUpdate(userId).orElseThrow();
			return jdbc.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout", Long.class);
		});

		assertThat(global).as("MySQL default stays untouched").isEqualTo(50L);
		assertThat(session).isEqualTo(LOCK_TIMEOUT_MILLIS / 1_000);
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

}
