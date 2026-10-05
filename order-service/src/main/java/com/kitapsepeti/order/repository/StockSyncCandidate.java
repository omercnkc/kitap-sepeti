package com.kitapsepeti.order.repository;

import java.util.UUID;

import com.kitapsepeti.order.entity.OrderStatus;
import com.kitapsepeti.order.entity.StockState;

/**
 * {@link StockSyncJob} için kilitsiz aday projeksiyonu.
 */
public record StockSyncCandidate(UUID id, OrderStatus status, StockState stockState) {
}
