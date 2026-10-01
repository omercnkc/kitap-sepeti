package com.kitapsepeti.catalog.entity;

import java.util.Locale;

/**
 * Kitabın yayın durumu. DB'de küçük harfle saklanır ({@link BookStatusConverter}); API'de de küçük harf
 * kullanılır ({@link #value()}, {@link #fromParameter(String)}).
 */
public enum BookStatus {
	/** Hazırlanıyor; katalogda görünmez. */
	DRAFT,
	/** Yayında; listelenir ve satılabilir. */
	PUBLISHED,
	/** Satıştan kaldırıldı; geçmiş siparişler için kayıt korunur. */
	ARCHIVED;

	/** API'deki karşılığı: {@code draft} | {@code published} | {@code archived}. */
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Query parametresi; büyük/küçük harf duyarsız. @throws IllegalArgumentException değer tanınmıyorsa */
	public static BookStatus fromParameter(String value) {
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}
}
