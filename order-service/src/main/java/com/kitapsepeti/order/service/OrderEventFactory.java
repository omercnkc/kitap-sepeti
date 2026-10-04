package com.kitapsepeti.order.service;

import java.math.RoundingMode;
import java.util.UUID;

import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.service.event.CartCheckedOutEvent;
import com.kitapsepeti.order.service.event.OrderFailedEvent;
import com.kitapsepeti.order.service.event.OrderPaidEvent;
import org.springframework.stereotype.Component;

/** Order durum geçişlerinden dış olay payload'larını üretir. */
@Component
public class OrderEventFactory {

	public static final String ORDER_AGGREGATE = "order";

	public OrderPaidEvent paid(UUID eventId, Order order) {
		return new OrderPaidEvent(eventId, OrderPaidEvent.VERSION, order.getId(), order.getUserId(),
				order.getPaymentId(), order.getTotalAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
				order.getCurrency(), order.getItems().size(), order.getUpdatedAt());
	}

	public OrderFailedEvent failed(UUID eventId, Order order) {
		return new OrderFailedEvent(eventId, OrderFailedEvent.VERSION, order.getId(), order.getUserId(),
				order.getFailureCode(), order.getUpdatedAt());
	}

	public CartCheckedOutEvent cartCheckedOut(UUID eventId, Order order) {
		return new CartCheckedOutEvent(eventId, CartCheckedOutEvent.VERSION, order.getCartId(), order.getUserId(),
				order.getId(), order.getUpdatedAt());
	}

}
