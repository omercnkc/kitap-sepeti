package com.kitapsepeti.catalog.service;

import com.kitapsepeti.catalog.exception.InvalidFieldException;

/** Admin servislerinin ortak slug kuralı: açıkça verilen slug (desen/uzunluk request'te doğrulandı) ya da addan üretilen. */
final class Slugs {

	private Slugs() {
	}

	static String forCreate(String requestedSlug, String name, int maxLength) {
		if (requestedSlug != null) {
			return requestedSlug;
		}
		String generated = SlugGenerator.fromName(name);
		if (generated.isEmpty()) {
			throw new InvalidFieldException("slug", "cannot be generated from name; provide a slug");
		}
		if (generated.length() > maxLength) {
			throw new InvalidFieldException("slug", "generated slug is too long; provide a slug");
		}
		return generated;
	}

}
