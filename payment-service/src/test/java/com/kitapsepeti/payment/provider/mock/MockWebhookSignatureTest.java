package com.kitapsepeti.payment.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.exception.PaymentErrorCode;
import com.kitapsepeti.payment.exception.WebhookSignatureException;
import com.kitapsepeti.payment.support.InternalTestKeys;
import com.kitapsepeti.payment.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** İmza biçimi {@code sha256=hex(HMAC-SHA256(secret, "<ts>.<gövde>"))}; her ret aynı exception, ayırt edilmez. */
class MockWebhookSignatureTest {

	private static final Duration TOLERANCE = Duration.ofMinutes(5);

	private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

	private final String secret = InternalTestKeys.randomKey();

	private final byte[] body = "{\"eventId\":\"evt_1\",\"type\":\"payment.succeeded\"}".getBytes(StandardCharsets.UTF_8);

	private final MutableClock clock = new MutableClock();

	private final MockWebhookSigner signer = new MockWebhookSigner(secret);

	private final MockWebhookVerifier verifier = new MockWebhookVerifier(signer, TOLERANCE, clock);

	private final long now = NOW.getEpochSecond();

	@BeforeEach
	void fixClock() {
		clock.fixAt(NOW);
	}

	@Test
	void signatureIsPrefixedLowerCaseHexOfHmacOverTimestampDotBody() throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		byte[] expected = mac.doFinal((now + "." + new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));

		String signature = signer.sign(now, body);

		assertThat(signature).isEqualTo("sha256=" + HexFormat.of().formatHex(expected)).matches("^sha256=[0-9a-f]{64}$");
		assertThat(MockWebhookSigner.TIMESTAMP_HEADER).isEqualTo("X-Mock-Timestamp");
		assertThat(MockWebhookSigner.SIGNATURE_HEADER).isEqualTo("X-Mock-Signature");
	}

	@Test
	void validSignaturePasses() {
		assertThatCode(() -> verifier.verify(Long.toString(now), signer.sign(now, body), body)).doesNotThrowAnyException();
	}

	@Test
	void anySingleChangedBodyByteFails() {
		String signature = signer.sign(now, body);
		for (int i = 0; i < body.length; i++) {
			byte[] tampered = Arrays.copyOf(body, body.length);
			tampered[i] ^= 0x01;
			assertRejected(Long.toString(now), signature, tampered);
		}
		assertRejected(Long.toString(now), signature, Arrays.copyOf(body, body.length + 1));
		assertRejected(Long.toString(now), signature, new byte[0]);
	}

	@Test
	void signatureFromAnotherSecretOrTimestampFails() {
		MockWebhookSigner other = new MockWebhookSigner(InternalTestKeys.randomKey());
		assertRejected(Long.toString(now), other.sign(now, body), body);
		assertRejected(Long.toString(now), signer.sign(now - 1, body), body);
	}

	@Test
	void missingHeadersFail() {
		assertRejected(null, signer.sign(now, body), body);
		assertRejected(Long.toString(now), null, body);
		assertRejected(null, null, body);
	}

	@Test
	void malformedSignatureFails() {
		String hex = signer.sign(now, body).substring("sha256=".length());
		for (String signature : new String[] { hex, "sha1=" + hex, "SHA256=" + hex, "sha256:" + hex, "sha256= " + hex,
				"sha256=" + hex.substring(1), "sha256=" + hex + "00", "sha256=" + "g".repeat(64),
				"sha256=" + hex.substring(0, 62) + "zz", "sha256=", "" }) {
			assertRejected(Long.toString(now), signature, body);
		}
	}

	@Test
	void timestampExactlyAtTheToleranceBoundaryPasses() {
		long past = now - TOLERANCE.toSeconds();
		long future = now + TOLERANCE.toSeconds();
		assertThatCode(() -> verifier.verify(Long.toString(past), signer.sign(past, body), body)).doesNotThrowAnyException();
		assertThatCode(() -> verifier.verify(Long.toString(future), signer.sign(future, body), body))
			.doesNotThrowAnyException();
	}

	@Test
	void timestampOneSecondBeyondToleranceFailsInBothDirections() {
		long past = now - TOLERANCE.toSeconds() - 1;
		long future = now + TOLERANCE.toSeconds() + 1;
		assertRejected(Long.toString(past), signer.sign(past, body), body);
		assertRejected(Long.toString(future), signer.sign(future, body), body);

		clock.advance(Duration.ofMinutes(10));
		assertRejected(Long.toString(now), signer.sign(now, body), body);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "abc", "-5", "+1791115200", "1791115200.0", "1.7e9", " 1791115200",
			"1791115200 ", "0x6A", "99999999999999999999" })
	void nonNumericTimestampFails(String timestamp) {
		assertRejected(timestamp, signer.sign(now, body), body);
	}

	@Test
	void secretMustBeAtLeastThirtyTwoCharactersAndIsNeverEchoed() {
		String shortSecret = secret.substring(0, 31);
		for (String invalid : new String[] { null, "", "   ", shortSecret }) {
			assertThatIllegalStateException().isThrownBy(() -> new MockWebhookSigner(invalid))
				.withMessageContaining("app.payment.mock.webhook-secret")
				.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(shortSecret));
		}
		assertThatCode(() -> new MockWebhookSigner(secret.substring(0, 32))).doesNotThrowAnyException();
		assertThat(signer.toString()).doesNotContain(secret);
	}

	/** Her ret aynı kod, aynı açıklama, sağlayıcı mock: hangi kontrolün başarısız olduğu taşınmaz. */
	private void assertRejected(String timestamp, String signature, byte[] payload) {
		assertThatThrownBy(() -> verifier.verify(timestamp, signature, payload))
			.isInstanceOfSatisfying(WebhookSignatureException.class, ex -> {
				assertThat(ex.getErrorCode()).isEqualTo(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID);
				assertThat(ex.getMessage()).isEqualTo(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID.defaultDetail());
				assertThat(ex.getProvider()).isEqualTo(PaymentProviderType.MOCK);
				assertThat(ex.getCause()).isNull();
			});
	}

}
