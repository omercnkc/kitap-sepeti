package com.kitapsepeti.catalog.mapper;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * Yanıtlardaki ada göre sıralama Türkçe kurallarla yapılır; {@code String.compareTo} "Çocuk"u "Edebiyat"tan
 * sonraya koyardı. (RuleBasedCollator'ın compare'i senkronize; paylaşılan örnek güvenli.)
 */
public final class NameOrder {

	public static final Comparator<String> TURKISH = Collator.getInstance(Locale.forLanguageTag("tr"))::compare;

	private NameOrder() {
	}

}
