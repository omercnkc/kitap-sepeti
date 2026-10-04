package com.kitapsepeti.payment.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kitapsepeti.payment.support.InternalTestKeys;
import com.kitapsepeti.payment.support.WebhookTestSecrets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Uçtan uca akışın logu kimlik, tutar, imza ve sır içermez. Tek testte: 201 ile oluşan ödeme → otomatik webhook →
 * {@code succeeded} → outbox yayını; kuruşu .99 olan ödemenin {@code failed} akışı; ödeme GET'i (yol maskesi); imzası
 * tutmayan webhook (401), aynı sipariş farklı tutar (409) ve anahtarsız internal istek (401). Yalnızca bu test
 * sırasında yazılan log denetlenir (bağlam açılışı hariç).
 * <p>
 * Bilinen istisna (Adım 4): outbox yayını başarısız olursa WARN satırı outbox olay id'sini yazar (operatörün satırı
 * bulabilmesi için). Bu testte yayın hatası senaryosu yok; satırın hiç görünmediği de doğrulanır.
 */
@ExtendWith(OutputCaptureExtension.class)
class PaymentFlowLogHygieneIT extends MockFlowTestSupport {

	private static final Pattern HEX_64 = Pattern.compile("[0-9a-fA-F]{64}");

	private static final Pattern EPOCH_SECONDS = Pattern.compile("(?<![0-9])[0-9]{10}(?![0-9])");

	@Test
	void fullFlowLogContainsNoIdentifiersAmountsSignaturesOrSecrets(CapturedOutput output) {
		int start = output.toString().length();
		long startedAt = Instant.now().getEpochSecond();

		UUID orderId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		ApiResponse created = create(orderId, userId, "149.90");
		assertThat(created.status()).isEqualTo(201);
		UUID succeeded = created.paymentId();
		UUID failedOrderId = UUID.randomUUID();
		UUID failedUserId = UUID.randomUUID();
		ApiResponse failedCreated = create(failedOrderId, failedUserId, "149.99");
		assertThat(failedCreated.status()).isEqualTo(201);
		UUID failed = failedCreated.paymentId();

		await().atMost(TIMEOUT).until(() -> "succeeded".equals(status(succeeded)) && "failed".equals(status(failed)));
		await().atMost(TIMEOUT).until(() -> outboxCount() == 2 && jdbc.queryForObject(
				"SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class) == 0);
		assertThat(get(succeeded).status()).isEqualTo(200);

		String providerPaymentId = providerPaymentId(succeeded);
		String badSignatureHex = randomHex();
		long badTimestamp = Instant.now().getEpochSecond();
		String webhookBody = """
				{"eventId":"evt_%s","providerPaymentId":"%s","type":"payment.succeeded","amount":"149.90","currency":"TRY"}"""
			.formatted(UUID.randomUUID().toString().replace("-", ""), providerPaymentId);
		int rejectedWebhook = RestClient.create("http://localhost:" + port).post()
			.uri("/webhooks/mock")
			.contentType(MediaType.APPLICATION_JSON)
			.header(MockWebhookSigner.TIMESTAMP_HEADER, Long.toString(badTimestamp))
			.header(MockWebhookSigner.SIGNATURE_HEADER, MockWebhookSigner.SIGNATURE_PREFIX + badSignatureHex)
			.body(webhookBody.getBytes(StandardCharsets.UTF_8))
			.exchange((request, response) -> response.getStatusCode().value());
		assertThat(rejectedWebhook).isEqualTo(401);
		assertThat(create(orderId, userId, "150.00").status()).isEqualTo(409);
		int anonymous = RestClient.create("http://localhost:" + port).post()
			.uri("/internal/payments")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":149.90,\"currency\":\"TRY\"}".formatted(orderId,
					userId))
			.exchange((request, response) -> response.getStatusCode().value());
		assertThat(anonymous).isEqualTo(401);

		String log = output.toString().substring(start);
		long finishedAt = Instant.now().getEpochSecond();

		assertThat(log).as("senaryoların log satırları yakalandı")
			.contains("Webhook handled (provider=mock, type=payment.succeeded, outcome=APPLIED)")
			.contains("Webhook handled (provider=mock, type=payment.failed, outcome=APPLIED)")
			.contains("Internal request GET /internal/payments/:paymentId client=order-service")
			.contains("Rejected webhook: invalid signature (provider=mock)")
			.contains("POST /internal/payments -> PAYMENT_ORDER_MISMATCH")
			.contains("Rejected internal request POST /internal/payments -> UNAUTHORIZED")
			.doesNotContain("Outbox publish failed");

		List<String> forbidden = new ArrayList<>();
		for (UUID id : List.of(orderId, userId, succeeded, failedOrderId, failedUserId, failed)) {
			forbidden.add(id.toString());
			forbidden.add(id.toString().replace("-", ""));
		}
		forbidden.add(providerPaymentId);
		forbidden.add(providerPaymentId(failed));
		forbidden.addAll(providerEventIds());
		forbidden.addAll(jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM outbox", String.class));
		forbidden.addAll(List.of("149.90", "149.9", "149.99", "150.00", badSignatureHex,
				MockWebhookSigner.SIGNATURE_PREFIX, Long.toString(badTimestamp), WebhookTestSecrets.MOCK_SECRET,
				InternalTestKeys.ORDER_SERVICE_KEY, InternalTestKeys.ORDER_SERVICE_KEY_SHA256, "\"amount\"",
				"\"eventId\"", "\"providerPaymentId\"", "\"orderId\"", "\"userId\"", "\"failureCode\""));
		assertThat(forbidden).hasSizeGreaterThanOrEqualTo(30);
		assertThat(forbidden).filteredOn(log::contains).as("logda geçmemeli").isEmpty();

		assertThat(HEX_64.matcher(log).find()).as("64 haneli hex (imza / özet)").isFalse();
		assertThat(epochSecondsBetween(log, startedAt - 600, finishedAt + 600)).as("imza zaman damgası").isEmpty();
	}

	private String providerPaymentId(UUID paymentId) {
		return jdbc.queryForObject("SELECT provider_payment_id FROM payments WHERE id = UUID_TO_BIN(?)", String.class,
				paymentId.toString());
	}

	private static String randomHex() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	private static List<String> epochSecondsBetween(String log, long from, long to) {
		List<String> found = new ArrayList<>();
		Matcher matcher = EPOCH_SECONDS.matcher(log);
		while (matcher.find()) {
			long value = Long.parseLong(matcher.group());
			if (value >= from && value <= to) {
				found.add(matcher.group());
			}
		}
		return found;
	}

}
