package com.kitapsepeti.payment.support;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Mock webhook secret'ı; test JVM'inde her çalıştırmada yeniden üretilir (repoda sabit secret yok). Tam bağlam açan
 * her test {@link #register} ile verir; {@code .env}'deki gerçek secret testlerde kullanılmaz.
 */
public final class WebhookTestSecrets {

	/** 32 rastgele bayt, base64url: 43 karakter (yerel .env'deki biçimle aynı). */
	public static final String MOCK_SECRET = InternalTestKeys.randomKey();

	private WebhookTestSecrets() {
	}

	public static void register(DynamicPropertyRegistry registry) {
		registry.add("app.payment.mock.webhook-secret", () -> MOCK_SECRET);
	}

}
