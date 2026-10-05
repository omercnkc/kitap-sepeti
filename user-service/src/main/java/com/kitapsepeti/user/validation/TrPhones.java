package com.kitapsepeti.user.validation;

/**
 * TR cep telefonu: yalnız rakamlar; baştaki {@code 90} veya {@code 0} temizlenir → {@code ^5\d{9}$}.
 * Kanonik API/DB formatı: 10 hane, 5 ile başlar (örn. {@code 5551112233}).
 */
public final class TrPhones {

	private TrPhones() {
	}

	/** null/blank → null; geçerliyse kanonik 10 hane; geçersizse null. */
	public static String toCanonicalOrNull(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String digits = digitsOnly(raw);
		if (digits.startsWith("90") && digits.length() == 12) {
			digits = digits.substring(2);
		}
		else if (digits.startsWith("0") && digits.length() == 11) {
			digits = digits.substring(1);
		}
		return isCanonical(digits) ? digits : null;
	}

	/**
	 * null/blank geçerli (opsiyonel / profil silme). Doluysa normalize sonrası kanonik olmalı.
	 */
	public static boolean isValidOptional(String raw) {
		if (raw == null || raw.isBlank()) {
			return true;
		}
		return toCanonicalOrNull(raw) != null;
	}

	public static boolean isCanonical(String digits) {
		return digits != null && digits.matches("^5\\d{9}$");
	}

	private static String digitsOnly(String raw) {
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c >= '0' && c <= '9') {
				sb.append(c);
			}
		}
		return sb.toString();
	}

}
