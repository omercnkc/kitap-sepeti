package com.kitapsepeti.catalog.entity;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link BookStatus} değerini DB'ye küçük harfle yazar, okurken enum'a çevirir.
 * {@code @Enumerated} kullanılmaz; çünkü {@code ck_books_status} kısıtı yalnızca
 * {@code 'draft'}, {@code 'published'} ve {@code 'archived'} kabul eder, büyük harfi reddeder.
 */
@Converter
public class BookStatusConverter implements AttributeConverter<BookStatus, String> {

	@Override
	public String convertToDatabaseColumn(BookStatus status) {
		return status == null ? null : status.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public BookStatus convertToEntityAttribute(String value) {
		return value == null ? null : BookStatus.valueOf(value.toUpperCase(Locale.ROOT));
	}

}
