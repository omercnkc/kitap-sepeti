package com.kitapsepeti.payment.entity;

/** Ödeme sağlayıcısı. DB'de küçük harf ({@link PaymentProviderTypeConverter}); v1'de yalnızca {@link #MOCK} çalışır. */
public enum PaymentProviderType implements DbEnum {

	MOCK("mock"),
	IYZICO("iyzico"),
	PAYTR("paytr"),
	STRIPE("stripe");

	private final String dbValue;

	PaymentProviderType(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

}
