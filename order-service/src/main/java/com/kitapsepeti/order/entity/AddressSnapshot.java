package com.kitapsepeti.order.entity;

import java.util.regex.Pattern;

/**
 * Siparişin teslimat adresi: checkout isteğindeki adresin o anki kopyası ({@code orders.address_snapshot}, JSON nesnesi).
 * Alanlar ve uzunluklar user-service {@code addresses} tablosuyla aynı; etiket, id, kullanıcı ve varsayılan bilgisi yok.
 * Kişisel veri: {@link #toString()} alan değeri içermez, doğrulama mesajları yalnızca alan adını yazar.
 *
 * @param recipientName zorunlu, en fazla 120
 * @param phone zorunlu, en fazla 32
 * @param line1 zorunlu, en fazla 200
 * @param line2 isteğe bağlı, en fazla 200
 * @param district isteğe bağlı, en fazla 80
 * @param city zorunlu, en fazla 80
 * @param postalCode isteğe bağlı, en fazla 16
 * @param country ISO 3166-1 alpha-2, büyük harf (ör. {@code TR})
 */
public record AddressSnapshot(String recipientName, String phone, String line1, String line2, String district,
		String city, String postalCode, String country) {

	private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

	/** @throws IllegalArgumentException zorunlu alan boşsa, alan uzunluğu aşılıyorsa ya da ülke kodu geçersizse */
	public AddressSnapshot {
		required(recipientName, "recipientName", 120);
		required(phone, "phone", 32);
		required(line1, "line1", 200);
		optional(line2, "line2", 200);
		optional(district, "district", 80);
		required(city, "city", 80);
		optional(postalCode, "postalCode", 16);
		if (country == null || !COUNTRY.matcher(country).matches()) {
			throw new IllegalArgumentException("Address country must be two upper-case letters");
		}
	}

	@Override
	public String toString() {
		return "AddressSnapshot[redacted]";
	}

	private static void required(String value, String field, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Address " + field + " must not be blank");
		}
		optional(value, field, maxLength);
	}

	private static void optional(String value, String field, int maxLength) {
		if (value != null && value.length() > maxLength) {
			throw new IllegalArgumentException("Address " + field + " must be at most " + maxLength + " characters");
		}
	}

}
