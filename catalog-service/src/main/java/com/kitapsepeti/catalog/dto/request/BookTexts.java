package com.kitapsepeti.catalog.dto.request;

/** Kitap isteklerindeki opsiyonel metin alanları için ortak kurallar. */
final class BookTexts {

	/** TEXT kolonu 65.535 bayt; utf8mb4'te karakter başına en çok 4 bayt → 10.000 karakter güvenli sınır. */
	static final int MAX_DESCRIPTION_LENGTH = 10_000;

	private BookTexts() {
	}

	static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

}
