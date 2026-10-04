package com.kitapsepeti.payment.provider.mock;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;

import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.exception.WebhookSignatureException;

/**
 * Mock webhook imzasını doğrular ({@link MockWebhookSigner} biçimi). Başlıklardan biri yok, zaman damgası sayı değil
 * ya da saatten {@code tolerance}'tan fazla uzak (geçmiş ya da gelecek), imza biçimi bozuk ya da imza tutmuyor:
 * hepsi aynı {@link WebhookSignatureException}'dır; hangi kontrolün başarısız olduğu dışarıya ayırt edilmez.
 * İmza baytları {@link MessageDigest#isEqual} ile sabit zamanlı karşılaştırılır.
 */
public final class MockWebhookVerifier {

	private static final Pattern EPOCH_SECONDS = Pattern.compile("^[0-9]{1,18}$");

	private static final int SIGNATURE_HEX_LENGTH = 64;

	private final MockWebhookSigner signer;

	private final Duration tolerance;

	private final Clock clock;

	public MockWebhookVerifier(MockWebhookSigner signer, Duration tolerance, Clock clock) {
		this.signer = signer;
		this.tolerance = tolerance;
		this.clock = clock;
	}

	/** @throws WebhookSignatureException imza geçersizse */
	public void verify(String timestampHeader, String signatureHeader, byte[] rawBody) {
		if (timestampHeader == null || signatureHeader == null || !EPOCH_SECONDS.matcher(timestampHeader).matches()) {
			throw rejected();
		}
		long timestamp = Long.parseLong(timestampHeader);
		boolean fresh = Duration.between(Instant.ofEpochSecond(timestamp), this.clock.instant()).abs()
			.compareTo(this.tolerance) <= 0;
		byte[] provided = parseSignature(signatureHeader);
		boolean matches = provided != null && MessageDigest.isEqual(provided, this.signer.mac(timestamp, rawBody));
		if (!fresh || !matches) {
			throw rejected();
		}
	}

	private static byte[] parseSignature(String header) {
		if (!header.startsWith(MockWebhookSigner.SIGNATURE_PREFIX)) {
			return null;
		}
		String hex = header.substring(MockWebhookSigner.SIGNATURE_PREFIX.length());
		if (hex.length() != SIGNATURE_HEX_LENGTH) {
			return null;
		}
		try {
			return HexFormat.of().parseHex(hex);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static WebhookSignatureException rejected() {
		return new WebhookSignatureException(PaymentProviderType.MOCK);
	}

}
