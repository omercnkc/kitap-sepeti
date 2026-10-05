package com.kitapsepeti.order.service;

import java.util.Objects;
import java.util.UUID;

import com.kitapsepeti.order.gateway.CatalogGateway;
import com.kitapsepeti.order.gateway.CommitResult;
import com.kitapsepeti.order.gateway.NotPerformed;
import com.kitapsepeti.order.gateway.Rejected;
import com.kitapsepeti.order.gateway.ReleaseResult;
import com.kitapsepeti.order.gateway.Unknown;
import com.kitapsepeti.order.service.OrderTransactions.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Catalog stok commit ve release çağrılarını koordine eder ve dönen sonuca göre
 * siparişin stok durumunu kilitli olarak günceller.
 * <p>
 * Dış çağrı transaction dışında yapılır; sonuca göre FOR UPDATE kilidiyle geçiş uygulanır.
 * Dispatcher ve StockSyncJob aynı koordinasyonu kullanır.
 * Loglarda sipariş id'si, kullanıcı id'si veya tutar yer almaz.
 */
@Service
public class StockCoordinator {

	private static final Logger log = LoggerFactory.getLogger(StockCoordinator.class);

	private static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";

	private final CatalogGateway catalog;

	private final OrderTransactions transactions;

	public StockCoordinator(CatalogGateway catalog, OrderTransactions transactions) {
		this.catalog = catalog;
		this.transactions = transactions;
	}

	public StockOutcome processCommit(UUID orderId) {
		Objects.requireNonNull(orderId, "orderId");
		CommitResult result = this.catalog.commit(orderId);
		return switch (result) {
			case CommitResult.Committed committed -> applyCommit(orderId);
			case CommitResult.AlreadyReleased alreadyReleased -> applyLost(orderId);
			case Rejected rejected when rejected.httpStatus() == 404 && RESOURCE_NOT_FOUND.equals(rejected.code()) ->
				applyLost(orderId);
			case Rejected rejected -> {
				log.error("Stock commit rejected by catalog (status={}, code={})", rejected.httpStatus(),
						rejected.code());
				yield StockOutcome.HELD;
			}
			case NotPerformed notPerformed -> {
				log.warn("Stock commit not performed");
				yield StockOutcome.NOT_PERFORMED;
			}
			case Unknown unknown -> {
				log.warn("Stock commit unknown result");
				yield StockOutcome.HELD;
			}
		};
	}

	public StockOutcome processRelease(UUID orderId) {
		Objects.requireNonNull(orderId, "orderId");
		ReleaseResult result = this.catalog.release(orderId);
		return switch (result) {
			case ReleaseResult.Released released -> applyRelease(orderId);
			case ReleaseResult.AlreadyCommitted alreadyCommitted -> {
				log.error("Stock release conflict -> ALREADY_COMMITTED");
				yield StockOutcome.NO_OP;
			}
			case Rejected rejected -> {
				log.warn("Stock release rejected by catalog (status={}, code={})", rejected.httpStatus(),
						rejected.code());
				yield StockOutcome.NO_OP;
			}
			case NotPerformed notPerformed -> {
				log.warn("Stock release not performed");
				yield StockOutcome.NOT_PERFORMED;
			}
			case Unknown unknown -> {
				log.warn("Stock release unknown result");
				yield StockOutcome.NO_OP;
			}
		};
	}

	private StockOutcome applyCommit(UUID orderId) {
		try {
			Transition transition = this.transactions.markStockCommitted(orderId);
			if (!transition.applied()) {
				log.debug("Stock commit transition not applied (result={})", transition.result());
				return StockOutcome.NO_OP;
			}
			return StockOutcome.COMMITTED;
		}
		catch (RuntimeException ex) {
			log.warn("Stock commit transition failed unexpectedly");
			return StockOutcome.NO_OP;
		}
	}

	private StockOutcome applyLost(UUID orderId) {
		log.error("Stock commit lost -> STOCK_COMMIT_LOST");
		try {
			Transition transition = this.transactions.markStockLost(orderId);
			if (!transition.applied()) {
				log.debug("Stock lost transition not applied (result={})", transition.result());
				return StockOutcome.NO_OP;
			}
			return StockOutcome.LOST;
		}
		catch (RuntimeException ex) {
			log.warn("Stock lost transition failed unexpectedly");
			return StockOutcome.NO_OP;
		}
	}

	private StockOutcome applyRelease(UUID orderId) {
		try {
			Transition transition = this.transactions.markStockReleased(orderId);
			if (!transition.applied()) {
				log.debug("Stock release transition not applied (result={})", transition.result());
				return StockOutcome.NO_OP;
			}
			return StockOutcome.RELEASED;
		}
		catch (RuntimeException ex) {
			log.warn("Stock release transition failed unexpectedly");
			return StockOutcome.NO_OP;
		}
	}

}
