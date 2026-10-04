package com.kitapsepeti.payment.entity;

/** Sağlayıcının webhook olay türü; DB'de sağlayıcıdan bağımsız sabit adlar ({@link ProviderEventTypeConverter}). */
public enum ProviderEventType implements DbEnum {

	PAYMENT_SUCCEEDED("payment.succeeded"),
	PAYMENT_FAILED("payment.failed");

	private final String dbValue;

	ProviderEventType(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

}
