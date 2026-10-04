package com.kitapsepeti.payment.controller.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.kitapsepeti.payment.ApiTestSupport;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.provider.mock.MockWebhookSigner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Webhook testlerinin ortak yardımcıları: referanslı ödeme, imzalı istek, tablo sayıları. */
abstract class WebhookTestSupport extends ApiTestSupport {

	static final String MOCK_WEBHOOK = "/webhooks/mock";

	static final String AMOUNT = "149.99";

	@Autowired
	MockWebhookSigner signer;

	@Autowired
	Clock clock;

	/** {@code initiated}, mock referansı yazılmış ödeme (Adım 6'da sağlayıcı çağrısından sonraki durum). */
	Payment newPayment(String amount, String currency) {
		Payment payment = Payment.initiate(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(amount), currency,
				PaymentProviderType.MOCK, clock);
		payment.attachProviderReference("mock_" + UUID.randomUUID(), clock);
		return payments.saveAndFlush(payment);
	}

	Payment newPayment() {
		return newPayment(AMOUNT, "TRY");
	}

	static String newEventId() {
		return "evt_" + UUID.randomUUID().toString().replace("-", "");
	}

	static String succeeded(String eventId, Payment payment) {
		return body(eventId, payment.getProviderPaymentId(), "payment.succeeded", AMOUNT, "TRY", null);
	}

	static String failed(String eventId, Payment payment, String failureCode) {
		return body(eventId, payment.getProviderPaymentId(), "payment.failed", AMOUNT, "TRY", failureCode);
	}

	/** Değerler JSON metni; null olan alan gövdeye hiç yazılmaz. */
	static String body(String eventId, String providerPaymentId, String type, String amount, String currency,
			String failureCode) {
		StringBuilder json = new StringBuilder("{");
		appendField(json, "eventId", eventId);
		appendField(json, "providerPaymentId", providerPaymentId);
		appendField(json, "type", type);
		appendField(json, "amount", amount);
		appendField(json, "currency", currency);
		appendField(json, "failureCode", failureCode);
		return json.append('}').toString();
	}

	private static void appendField(StringBuilder json, String name, String value) {
		if (value == null) {
			return;
		}
		if (json.length() > 1) {
			json.append(',');
		}
		json.append('"').append(name).append("\":\"").append(value).append('"');
	}

	/** Şu anki zaman damgasıyla doğru imzalanmış JSON isteği. */
	MockHttpServletRequestBuilder signed(String body) {
		return signed(MOCK_WEBHOOK, body.getBytes(StandardCharsets.UTF_8), Instant.now().getEpochSecond());
	}

	MockHttpServletRequestBuilder signed(String path, byte[] body, long timestamp) {
		return post(path).contentType(MediaType.APPLICATION_JSON)
			.header(MockWebhookSigner.TIMESTAMP_HEADER, Long.toString(timestamp))
			.header(MockWebhookSigner.SIGNATURE_HEADER, signer.sign(timestamp, body))
			.content(body);
	}

	ResultActions send(String body) throws Exception {
		return mockMvc.perform(signed(body));
	}

	Map<String, Object> paymentRow(UUID paymentId) {
		return jdbc.queryForMap(
				"SELECT status, failure_code, provider_payment_id, updated_at FROM payments WHERE id = UUID_TO_BIN(?)",
				paymentId.toString());
	}

	List<Map<String, Object>> providerEventRows() {
		return jdbc.queryForList("""
				SELECT provider, provider_event_id, BIN_TO_UUID(payment_id) AS payment_id, event_type
				FROM provider_events ORDER BY processed_at, provider_event_id""");
	}

	int providerEventCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM provider_events", Integer.class);
	}

	int outboxCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class);
	}

	List<String> outboxTypes(UUID paymentId) {
		return jdbc.queryForList("SELECT event_type FROM outbox WHERE aggregate_id = UUID_TO_BIN(?)", String.class,
				paymentId.toString());
	}

	/** Görevler ayrı thread'lerde; hepsi hazır olunca tek latch ile aynı anda başlatılır. Sonuçlar görev sırasıyla. */
	static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		try {
			CountDownLatch ready = new CountDownLatch(tasks.size());
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return task.call();
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
