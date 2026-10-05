package com.kitapsepeti.order.service;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.kitapsepeti.order.service.event.StockCommitReadyEvent;
import com.kitapsepeti.order.service.event.StockReleaseReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ödeme sonucu transaction'ı commit edildikten sonra sınırlı bir executor havuzuna stok commit/release işi bırakır.
 * Kuyruk doluysa iş atlanır, WARN yazılır; StockSyncJob toparlar. Exception fırlatılmaz.
 * Loglar sipariş id'si, kullanıcı id'si veya tutar içermez.
 */
@Component
public class StockDispatcher implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(StockDispatcher.class);

	private final StockCoordinator coordinator;

	private final ExecutorService executor;

	@org.springframework.beans.factory.annotation.Autowired
	public StockDispatcher(StockCoordinator coordinator,
			@Value("${app.stock-sync.dispatcher.core-pool-size:1}") int corePoolSize,
			@Value("${app.stock-sync.dispatcher.max-pool-size:2}") int maxPoolSize,
			@Value("${app.stock-sync.dispatcher.queue-capacity:50}") int queueCapacity) {
		this(coordinator, new ThreadPoolExecutor(corePoolSize, maxPoolSize, 60L, TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(queueCapacity),
				runnable -> {
					Thread thread = new Thread(runnable, "stock-dispatcher");
					thread.setDaemon(true);
					return thread;
				},
				(runnable, executorInstance) -> {
					log.warn("Stock dispatch executor queue is full; skipping task, StockSyncJob will recover");
				}));
	}

	public StockDispatcher(StockCoordinator coordinator, ExecutorService executor) {
		this.coordinator = coordinator;
		this.executor = executor;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onStockCommitReady(StockCommitReadyEvent event) {
		dispatchCommit(event.orderId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onStockReleaseReady(StockReleaseReadyEvent event) {
		dispatchRelease(event.orderId());
	}

	public void dispatchCommit(UUID orderId) {
		Objects.requireNonNull(orderId, "orderId");
		dispatch(() -> this.coordinator.processCommit(orderId));
	}

	public void dispatchRelease(UUID orderId) {
		Objects.requireNonNull(orderId, "orderId");
		dispatch(() -> this.coordinator.processRelease(orderId));
	}

	private void dispatch(Runnable task) {
		try {
			this.executor.execute(() -> {
				try {
					task.run();
				}
				catch (Throwable ex) {
					log.warn("Unexpected failure during stock dispatch execution");
				}
			});
		}
		catch (RejectedExecutionException ex) {
			log.warn("Stock dispatch executor queue is full; skipping task, StockSyncJob will recover");
		}
	}

	@Override
	public void destroy() {
		this.executor.shutdown();
		try {
			if (!this.executor.awaitTermination(2, TimeUnit.SECONDS)) {
				this.executor.shutdownNow();
			}
		}
		catch (InterruptedException ex) {
			this.executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

}
