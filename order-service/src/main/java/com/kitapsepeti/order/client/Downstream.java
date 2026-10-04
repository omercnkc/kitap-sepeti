package com.kitapsepeti.order.client;

/** Order'ın çağırdığı servisler; ad hem Feign istemci adı hem circuit breaker instance adıdır. */
public enum Downstream {

	CART("cart"), CATALOG("catalog"), PAYMENT("payment");

	private final String id;

	Downstream(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}

}
