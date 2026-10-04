package com.kitapsepeti.payment.provider.mock;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mock sağlayıcının webhook imzası: {@code sha256=<hex(HMAC-SHA256(secret, "<timestamp>.<ham gövde>"))>}.
 * Zaman damgası epoch saniyedir ve {@value #TIMESTAMP_HEADER} başlığında, imza {@value #SIGNATURE_HEADER} başlığında
 * taşınır. Anahtar, secret metninin UTF-8 baytlarıdır. Doğrulama {@link MockWebhookVerifier}'da; webhook gönderen
 * taraf (mock dispatcher) da bu sınıfı kullanır.
 */
public final class MockWebhookSigner {

	public static final String TIMESTAMP_HEADER = "X-Mock-Timestamp";

	public static final String SIGNATURE_HEADER = "X-Mock-Signature";

	public static final String SIGNATURE_PREFIX = "sha256=";

	public static final int MIN_SECRET_LENGTH = 32;

	static final String SECRET_PROPERTY = "app.payment.mock.webhook-secret";

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	/**
	 * @param secret en az {@value #MIN_SECRET_LENGTH} karakter
	 * @throws IllegalStateException secret yok, boş ya da kısaysa (mesaj değeri içermez)
	 */
	public MockWebhookSigner(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException(SECRET_PROPERTY
					+ " must be set (env PAYMENT_MOCK_WEBHOOK_SECRET); payment-service does not start without it");
		}
		if (secret.length() < MIN_SECRET_LENGTH) {
			throw new IllegalStateException(SECRET_PROPERTY + " must be at least " + MIN_SECRET_LENGTH + " characters");
		}
		this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	/** @return {@value #SIGNATURE_HEADER} başlığının değeri */
	public String sign(long timestampEpochSeconds, byte[] rawBody) {
		return SIGNATURE_PREFIX + HexFormat.of().formatHex(mac(timestampEpochSeconds, rawBody));
	}

	byte[] mac(long timestampEpochSeconds, byte[] rawBody) {
		Mac mac = newMac();
		mac.update(Long.toString(timestampEpochSeconds).getBytes(StandardCharsets.US_ASCII));
		mac.update((byte) '.');
		return mac.doFinal(rawBody);
	}

	/** Mac thread-safe değil; her imza kendi örneğini kullanır. */
	private Mac newMac() {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(this.key);
			return mac;
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("HMAC-SHA256 is not available", ex);
		}
	}

	@Override
	public String toString() {
		return "MockWebhookSigner[secret=***]";
	}

}
