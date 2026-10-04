package com.kitapsepeti.order.service;

import java.util.UUID;

import com.kitapsepeti.order.dto.response.OrderResponse;
import com.kitapsepeti.order.exception.OrderErrorCode;
import com.kitapsepeti.order.exception.OrderProblemException;
import org.springframework.stereotype.Service;

/** Sipariş okuma. Başka kullanıcının siparişi olmayan siparişle aynı yanıtı alır (404; varlığı sızmaz). */
@Service
public class OrderQueryService {

	private final OrderTransactions transactions;

	public OrderQueryService(OrderTransactions transactions) {
		this.transactions = transactions;
	}

	/** @throws OrderProblemException {@code ORDER_NOT_FOUND}: sipariş yok ya da kullanıcıya ait değil */
	public OrderResponse get(UUID userId, UUID orderId) {
		return this.transactions.findOwned(orderId, userId)
			.orElseThrow(() -> new OrderProblemException(OrderErrorCode.ORDER_NOT_FOUND));
	}

}
