package com.kitapsepeti.cart.entity;

import java.util.Arrays;
import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link CartStatus} değerini DB'ye küçük harfle yazar ({@code CHECKED_OUT} → {@code 'checked_out'}), okurken enum'a çevirir.
 * {@code @Enumerated} kullanılmaz; {@code ck_carts_status} yalnızca küçük harfli değerleri kabul eder. Okuma da birebir
 * eşleşme ister: kolon {@code utf8mb4_bin} olduğu için DB'de başka yazım bulunamaz, bulunursa veri bozuktur.
 */
@Converter
public class CartStatusConverter implements AttributeConverter<CartStatus, String> {

	@Override
	public String convertToDatabaseColumn(CartStatus status) {
		return status == null ? null : toDbValue(status);
	}

	@Override
	public CartStatus convertToEntityAttribute(String value) {
		if (value == null) {
			return null;
		}
		for (CartStatus status : CartStatus.values()) {
			if (toDbValue(status).equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown cart status in database: '" + value + "'; expected one of "
				+ Arrays.stream(CartStatus.values()).map(CartStatusConverter::toDbValue).toList());
	}

	private static String toDbValue(CartStatus status) {
		return status.name().toLowerCase(Locale.ROOT);
	}

}
