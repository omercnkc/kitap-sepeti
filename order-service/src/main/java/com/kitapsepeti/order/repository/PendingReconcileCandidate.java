package com.kitapsepeti.order.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.kitapsepeti.order.entity.StockState;

/**
 * {@code PendingReconciliationJob} için kilitsiz aday projeksiyonu: Payment'a checkout'takiyle aynı isteği atmak için
 * gereken alanlar. {@code paymentId} null olabilir.
 */
public record PendingReconcileCandidate(UUID id, UUID userId, StockState stockState, BigDecimal totalAmount,
		String currency, UUID paymentId, Instant createdAt) {

	@Override
	public String toString() {
		return "PendingReconcileCandidate[stockState=" + this.stockState + "]";
	}

}
