package com.kitapsepeti.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DbEnumConvertersTest {

	private final PaymentStatusConverter status = new PaymentStatusConverter();

	private final PaymentProviderTypeConverter provider = new PaymentProviderTypeConverter();

	private final ProviderEventTypeConverter eventType = new ProviderEventTypeConverter();

	@ParameterizedTest
	@CsvSource({ "INITIATED, initiated", "SUCCEEDED, succeeded", "FAILED, failed" })
	void paymentStatusRoundTrips(PaymentStatus value, String dbValue) {
		assertThat(status.convertToDatabaseColumn(value)).isEqualTo(dbValue);
		assertThat(status.convertToEntityAttribute(dbValue)).isEqualTo(value);
	}

	@ParameterizedTest
	@CsvSource({ "MOCK, mock", "IYZICO, iyzico", "PAYTR, paytr", "STRIPE, stripe" })
	void providerTypeRoundTrips(PaymentProviderType value, String dbValue) {
		assertThat(provider.convertToDatabaseColumn(value)).isEqualTo(dbValue);
		assertThat(provider.convertToEntityAttribute(dbValue)).isEqualTo(value);
	}

	@ParameterizedTest
	@CsvSource({ "PAYMENT_SUCCEEDED, payment.succeeded", "PAYMENT_FAILED, payment.failed" })
	void eventTypeRoundTrips(ProviderEventType value, String dbValue) {
		assertThat(eventType.convertToDatabaseColumn(value)).isEqualTo(dbValue);
		assertThat(eventType.convertToEntityAttribute(dbValue)).isEqualTo(value);
	}

	@Test
	void nullStaysNull() {
		assertThat(status.convertToDatabaseColumn(null)).isNull();
		assertThat(status.convertToEntityAttribute(null)).isNull();
		assertThat(provider.convertToEntityAttribute(null)).isNull();
		assertThat(eventType.convertToEntityAttribute(null)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Initiated", "SUCCEEDED", "pending", "", " initiated" })
	void unknownPaymentStatusFails(String dbValue) {
		assertThatThrownBy(() -> status.convertToEntityAttribute(dbValue))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Unknown payment status in database: '" + dbValue
					+ "'; expected one of [initiated, succeeded, failed]");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Mock", "MOCK", "paypal", "" })
	void unknownProviderFails(String dbValue) {
		assertThatThrownBy(() -> provider.convertToEntityAttribute(dbValue))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Unknown payment provider in database: '" + dbValue
					+ "'; expected one of [mock, iyzico, paytr, stripe]");
	}

	@ParameterizedTest
	@ValueSource(strings = { "PAYMENT.SUCCEEDED", "payment_succeeded", "PAYMENT_SUCCEEDED", "payment.refunded", "" })
	void unknownEventTypeFails(String dbValue) {
		assertThatThrownBy(() -> eventType.convertToEntityAttribute(dbValue))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Unknown provider event type in database: '" + dbValue
					+ "'; expected one of [payment.succeeded, payment.failed]");
	}

}
