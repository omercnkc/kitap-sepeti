package com.kitapsepeti.order.entity;

import jakarta.persistence.Converter;

@Converter
public class OrderStatusConverter extends StrictDbEnumConverter<OrderStatus> {

	public OrderStatusConverter() {
		super(OrderStatus.class, "order status");
	}

}
