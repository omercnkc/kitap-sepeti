package com.kitapsepeti.cart.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * order-service'in internal anahtarı ve özeti; test JVM'inde her çalıştırmada yeniden üretilir (repoda sabit anahtar yok).
 * Tam bağlam açan her test {@link #register} ile özeti verir; {@code .env}'deki gerçek özet testlerde kullanılmaz.
 */
public final class InternalTestKeys {

	public static final String ORDER_SERVICE_KEY = randomKey();

	public static final String ORDER_SERVICE_KEY_SHA256 = sha256Hex(ORDER_SERVICE_KEY);

	private InternalTestKeys() {
	}

	public static void register(DynamicPropertyRegistry registry) {
		registry.add("app.internal-auth.clients[0].name", () -> "order-service");
		registry.add("app.internal-auth.clients[0].key-sha256", () -> ORDER_SERVICE_KEY_SHA256);
	}

	public static String randomKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String sha256Hex(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
