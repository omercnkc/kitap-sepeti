package com.kitapsepeti.order.entity;

/**
 * Siparişin durumu. DB'de küçük harf ({@link OrderStatusConverter}). Geçişler yalnızca {@link #PENDING}'den:
 * {@link Order#markPaid} ve {@link Order#markFailed}; son durumlar ({@link #PAID}, {@link #FAILED}) değişmez.
 * v1'de iptal yok; ödeme zaman aşımı {@link #FAILED} + {@code ORDER_EXPIRED}.
 */
public enum OrderStatus implements DbEnum {

	PENDING("pending"),
	PAID("paid"),
	FAILED("failed");

	private final String dbValue;

	OrderStatus(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

}
