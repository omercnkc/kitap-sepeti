package com.kitapsepeti.catalog.dto.request;

import java.util.Locale;

/**
 * Kitap listesi sıralaması. Query'de büyük/küçük harf duyarsız ({@code newest}, {@code price_asc}...);
 * dönüşüm {@link #fromParameter(String)} ile yapılır, tanınmayan değer binding hatası (400) olur.
 * Her sıralamaya kararlı sayfalama için ikincil olarak {@code id} eklenir (BookQueryService).
 */
public enum BookSort {

	/** {@code published_at} yeniden eskiye. */
	NEWEST,
	PRICE_ASC,
	PRICE_DESC,
	TITLE_ASC;

	/** @throws IllegalArgumentException değer tanınmıyorsa */
	public static BookSort fromParameter(String value) {
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}

}
