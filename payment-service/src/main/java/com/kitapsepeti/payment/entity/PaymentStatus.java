package com.kitapsepeti.payment.entity;

/**
 * Ödemenin durumu. DB'de küçük harf ({@link PaymentStatusConverter}). Geçişler yalnızca {@link #INITIATED}'dan:
 * {@link Payment#succeed} ve {@link Payment#fail}; son durumlar ({@link #SUCCEEDED}, {@link #FAILED}) değişmez.
 */
public enum PaymentStatus implements DbEnum {

	INITIATED("initiated"),
	SUCCEEDED("succeeded"),
	FAILED("failed");

	private final String dbValue;

	PaymentStatus(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

	public boolean isFinal() {
		return this != INITIATED;
	}

}
