package com.kitapsepeti.catalog.validation;

/**
 * ISBN normalizasyonu ve checksum kontrolü (Spring'e bağımlı değil). Normal biçim: tire ve boşluksuz,
 * ISBN-10'un sondaki kontrol karakteri büyük {@code X}. DB'de bu biçim saklanır.
 */
public final class Isbns {

	private Isbns() {
	}

	/** Tire ve boşlukları siler, küçük {@code x}'i {@code X} yapar; null → null. Geçerliliği kontrol ETMEZ. */
	public static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		StringBuilder normalized = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '-' || Character.isWhitespace(c)) {
				continue;
			}
			normalized.append(c == 'x' ? 'X' : c);
		}
		return normalized.toString();
	}

	/** Normalize edilmiş değer geçerli bir ISBN-10 veya ISBN-13 mü (checksum dahil). */
	public static boolean isValid(String normalized) {
		if (normalized == null) {
			return false;
		}
		return switch (normalized.length()) {
			case 10 -> isValidIsbn10(normalized);
			case 13 -> isValidIsbn13(normalized);
			default -> false;
		};
	}

	/** Ağırlıklar 10..1; son karakter {@code X} = 10 olabilir; toplam 11'e bölünmeli. */
	private static boolean isValidIsbn10(String isbn) {
		int sum = 0;
		for (int i = 0; i < 10; i++) {
			char c = isbn.charAt(i);
			int digit;
			if (isAsciiDigit(c)) {
				digit = c - '0';
			}
			else if (c == 'X' && i == 9) {
				digit = 10;
			}
			else {
				return false;
			}
			sum += digit * (10 - i);
		}
		return sum % 11 == 0;
	}

	/** Ağırlıklar sırayla 1 ve 3; toplam 10'a bölünmeli. */
	private static boolean isValidIsbn13(String isbn) {
		int sum = 0;
		for (int i = 0; i < 13; i++) {
			char c = isbn.charAt(i);
			if (!isAsciiDigit(c)) {
				return false;
			}
			sum += (c - '0') * ((i % 2 == 0) ? 1 : 3);
		}
		return sum % 10 == 0;
	}

	/** {@link Character#isDigit} Unicode rakamlarını (ör. Arapça-Hint) da kabul eder; burada yalnızca 0-9. */
	private static boolean isAsciiDigit(char c) {
		return c >= '0' && c <= '9';
	}

}
