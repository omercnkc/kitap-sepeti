package com.kitapsepeti.catalog.repository;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

/**
 * Büyük/küçük harf duyarsız LIKE alt dizgi (tr-TR). Kitap başlığı ve yazar adı aramalarında ortak.
 */
public final class CaseInsensitiveContains {

	private static final Locale TR = Locale.forLanguageTag("tr-TR");

	private CaseInsensitiveContains() {
	}

	public static <T> Specification<T> of(String attribute, String q) {
		String pattern = "%" + q.toLowerCase(TR) + "%";
		return (root, query, cb) -> cb.like(cb.lower(root.get(attribute)), pattern);
	}

}
