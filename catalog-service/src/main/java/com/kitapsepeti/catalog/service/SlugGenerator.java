package com.kitapsepeti.catalog.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Addan URL slug'ı üretir: Türkçe harfler ASCII karşılığına çevrilir, diğer aksanlar atılır (é → e),
 * küçük harfe çevrilir, harf/rakam dışındaki her dizi tek "-" olur, baştaki/sondaki "-" atılır.
 * Sonuç {@code ^[a-z0-9]+(-[a-z0-9]+)*$} desenine uyar ya da boştur (ad hiç harf/rakam içermiyorsa).
 */
public final class SlugGenerator {

	private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

	private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

	private SlugGenerator() {
	}

	/** @return slug; ad harf/rakam içermiyorsa boş metin */
	public static String fromName(String name) {
		String ascii = Normalizer.normalize(transliterateTurkish(name), Normalizer.Form.NFD);
		String lower = COMBINING_MARKS.matcher(ascii).replaceAll("").toLowerCase(Locale.ROOT);
		String hyphenated = NON_ALPHANUMERIC.matcher(lower).replaceAll("-");
		return EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
	}

	/** {@code toLowerCase(Locale.ROOT)} "İ"yi "i̇" (i + birleşik nokta) yapar, "ı"yı korur; bu yüzden önce açıkça çevrilir. */
	private static String transliterateTurkish(String value) {
		StringBuilder result = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			result.append(switch (c) {
				case 'ç', 'Ç' -> 'c';
				case 'ğ', 'Ğ' -> 'g';
				case 'ı', 'İ' -> 'i';
				case 'ö', 'Ö' -> 'o';
				case 'ş', 'Ş' -> 's';
				case 'ü', 'Ü' -> 'u';
				default -> c;
			});
		}
		return result.toString();
	}

}
