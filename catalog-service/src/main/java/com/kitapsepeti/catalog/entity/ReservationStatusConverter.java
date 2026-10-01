package com.kitapsepeti.catalog.entity;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link ReservationStatus} değerini DB'ye küçük harfle yazar, okurken enum'a çevirir.
 * {@code @Enumerated} kullanılmaz; çünkü {@code ck_stock_reservations_status} kısıtı yalnızca
 * {@code 'held'}, {@code 'committed'} ve {@code 'released'} kabul eder, büyük harfi reddeder.
 */
@Converter
public class ReservationStatusConverter implements AttributeConverter<ReservationStatus, String> {

	@Override
	public String convertToDatabaseColumn(ReservationStatus status) {
		return status == null ? null : status.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public ReservationStatus convertToEntityAttribute(String value) {
		return value == null ? null : ReservationStatus.valueOf(value.toUpperCase(Locale.ROOT));
	}

}
