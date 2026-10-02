package com.kitapsepeti.cart.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CartStatusConverterTest {

	private final CartStatusConverter converter = new CartStatusConverter();

	@ParameterizedTest
	@CsvSource({ "ACTIVE, active", "CHECKED_OUT, checked_out", "ABANDONED, abandoned" })
	void writesAndReadsLowerCaseValues(CartStatus status, String dbValue) {
		assertThat(converter.convertToDatabaseColumn(status)).isEqualTo(dbValue);
		assertThat(converter.convertToEntityAttribute(dbValue)).isEqualTo(status);
	}

	@Test
	void nullStaysNull() {
		assertThat(converter.convertToDatabaseColumn(null)).isNull();
		assertThat(converter.convertToEntityAttribute(null)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "ACTIVE", "Active", "expired", "", "checked-out" })
	void unknownValueFailsWithValueAndAllowedValues(String dbValue) {
		assertThatThrownBy(() -> converter.convertToEntityAttribute(dbValue))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Unknown cart status in database: '" + dbValue
					+ "'; expected one of [active, checked_out, abandoned]");
	}

}
