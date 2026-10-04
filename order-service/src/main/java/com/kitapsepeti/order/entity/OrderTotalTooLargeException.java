package com.kitapsepeti.order.entity;

/**
 * {@link Order#place}: ara toplam DECIMAL(12,2)'ye ({@link Order#MAX_AMOUNT}) sığmıyor. {@link IllegalArgumentException}
 * olarak kalır (domain açısından geçersiz girdi); checkout ucu bu alt türü ayrıca yakalayıp 422 döner.
 */
public class OrderTotalTooLargeException extends IllegalArgumentException {

	public OrderTotalTooLargeException() {
		super("Order total must not exceed " + Order.MAX_AMOUNT);
	}

}
