package com.kitapsepeti.order.client;

/**
 * Order'ın Cart, Catalog ve Payment {@code /internal/**} uçlarına gönderdiği ham anahtar ({@code ORDER_INTERNAL_API_KEY}).
 * Karşı servisler yalnızca SHA-256 özetini bilir. Yok/boş ya da başlığa yazılamayan anahtarla uygulama açılmaz (internal
 * özet politikasıyla aynı: fail-fast, hata mesajında değer yok). {@link #toString()} değeri içermez.
 */
public final class InternalApiKey {

	public static final String HEADER = "X-Internal-Api-Key";

	static final String PROPERTY = "app.clients.internal-api-key (ORDER_INTERNAL_API_KEY)";

	private final String value;

	private InternalApiKey(String value) {
		this.value = value;
	}

	/** @throws IllegalStateException anahtar yok, boş ya da boşluk/kontrol/ASCII dışı karakter içeriyor */
	public static InternalApiKey of(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalStateException(PROPERTY + " must be set to the raw internal API key");
		}
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c <= ' ' || c > '~') {
				throw new IllegalStateException(
						PROPERTY + " must contain only printable ASCII characters without spaces (configured value not shown)");
			}
		}
		return new InternalApiKey(raw);
	}

	String value() {
		return this.value;
	}

	@Override
	public String toString() {
		return "InternalApiKey[redacted]";
	}

}
