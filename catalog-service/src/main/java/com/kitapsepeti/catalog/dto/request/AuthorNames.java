package com.kitapsepeti.catalog.dto.request;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Kitap create/update gövdesindeki yazar adlarını sıkıştırır (boşları atar, tr-TR ile tekilleştirir). */
final class AuthorNames {

	private static final Locale TR = Locale.forLanguageTag("tr-TR");

	private AuthorNames() {
	}

	static List<String> compact(List<String> raw) {
		if (raw == null || raw.isEmpty()) {
			return List.of();
		}
		Map<String, String> byKey = new LinkedHashMap<>();
		for (String item : raw) {
			if (item == null) {
				continue;
			}
			String stripped = item.strip();
			if (stripped.isEmpty()) {
				continue;
			}
			byKey.putIfAbsent(normalizeKey(stripped), stripped);
		}
		return List.copyOf(byKey.values());
	}

	static String normalizeKey(String name) {
		return name.toLowerCase(TR);
	}

}
