package com.kitapsepeti.payment.entity;

import java.util.Arrays;

import jakarta.persistence.AttributeConverter;

/**
 * Enum'u {@link DbEnum#dbValue()} ile yazar, okurken birebir eşleşme ister. {@code @Enumerated} kullanılmaz; kolonlar
 * {@code utf8mb4_bin} olduğu için DB'de başka yazım bulunamaz, bulunursa veri bozuktur ve exception fırlar
 * (cart {@code CartStatusConverter} ile aynı katılık).
 */
abstract class StrictDbEnumConverter<E extends Enum<E> & DbEnum> implements AttributeConverter<E, String> {

	private final E[] values;

	private final String label;

	StrictDbEnumConverter(Class<E> type, String label) {
		this.values = type.getEnumConstants();
		this.label = label;
	}

	@Override
	public String convertToDatabaseColumn(E value) {
		return value == null ? null : value.dbValue();
	}

	@Override
	public E convertToEntityAttribute(String value) {
		if (value == null) {
			return null;
		}
		for (E candidate : values) {
			if (candidate.dbValue().equals(value)) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("Unknown " + label + " in database: '" + value + "'; expected one of "
				+ Arrays.stream(values).map(DbEnum::dbValue).toList());
	}

}
