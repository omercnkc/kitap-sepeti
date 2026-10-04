package com.kitapsepeti.payment.controller.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationEntryPoint;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.exception.WebhookSignatureException;
import com.kitapsepeti.payment.provider.mock.MockWebhookSigner;
import com.kitapsepeti.payment.support.InternalTestKeys;
import com.kitapsepeti.payment.support.WebhookTestSecrets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /webhooks/mock}: imza → JSON → doğrulama → olay kaydı + durum geçişi + outbox (tek transaction).
 * İşlenen ve tekrar olay 204; reddedilen istek DB'de hiçbir şey değiştirmez.
 */
@ExtendWith(OutputCaptureExtension.class)
class WebhookControllerTest extends WebhookTestSupport {

	private static final String CONFLICT_WARNING = "Conflicting payment result ignored";

	private static final String SIGNATURE_WARNING = "Rejected webhook: invalid signature (provider=mock)";

	@Autowired
	private JsonMapper jsonMapper;

	// --- işlenen olaylar ---

	@Test
	void succeededWebhookAppliesTransitionRecordsEventAndWritesOneOutboxRow() throws Exception {
		Payment payment = newPayment();
		String eventId = newEventId();

		send(succeeded(eventId, payment)).andExpect(status().isNoContent()).andExpect(content().string(""));

		assertThat(paymentRow(payment.getId())).containsEntry("status", "succeeded").containsEntry("failure_code", null);
		assertThat(providerEventRows()).containsExactly(Map.of("provider", "mock", "provider_event_id", eventId,
				"payment_id", payment.getId().toString(), "event_type", "payment.succeeded"));
		assertThat(outboxTypes(payment.getId())).containsExactly("PaymentSucceeded");
		assertThat(outboxCount()).isEqualTo(1);
	}

	@Test
	void sameEventDeliveredAgainIsAcknowledgedAndChangesNothing() throws Exception {
		Payment payment = newPayment();
		String body = succeeded(newEventId(), payment);
		send(body).andExpect(status().isNoContent());
		Map<String, Object> after = paymentRow(payment.getId());

		send(body).andExpect(status().isNoContent()).andExpect(content().string(""));
		send(body).andExpect(status().isNoContent());

		assertThat(paymentRow(payment.getId())).isEqualTo(after);
		assertThat(providerEventCount()).isEqualTo(1);
		assertThat(outboxCount()).isEqualTo(1);
	}

	@Test
	void differentEventWithSameResultIsRecordedButProducesNoSecondOutboxRow() throws Exception {
		Payment payment = newPayment();
		send(succeeded(newEventId(), payment)).andExpect(status().isNoContent());
		Map<String, Object> after = paymentRow(payment.getId());

		send(succeeded(newEventId(), payment)).andExpect(status().isNoContent());

		assertThat(paymentRow(payment.getId())).isEqualTo(after);
		assertThat(providerEventCount()).isEqualTo(2);
		assertThat(outboxCount()).isEqualTo(1);
	}

	@Test
	void failedWebhookForSucceededPaymentIsRecordedChangesNothingAndWarnsWithoutValues(CapturedOutput output)
			throws Exception {
		Payment payment = newPayment();
		send(succeeded(newEventId(), payment)).andExpect(status().isNoContent());
		Map<String, Object> after = paymentRow(payment.getId());
		String failedEvent = newEventId();

		send(failed(failedEvent, payment, "INSUFFICIENT_FUNDS")).andExpect(status().isNoContent());

		assertThat(paymentRow(payment.getId())).isEqualTo(after).containsEntry("status", "succeeded");
		assertThat(providerEventRows()).extracting(row -> row.get("event_type"))
			.containsExactlyInAnyOrder("payment.succeeded", "payment.failed");
		assertThat(outboxTypes(payment.getId())).containsExactly("PaymentSucceeded");
		List<String> warnings = output.getAll().lines().filter(line -> line.contains(CONFLICT_WARNING)).toList();
		assertThat(warnings).hasSize(1).allSatisfy(line -> assertThat(line).contains(" WARN ").endsWith(CONFLICT_WARNING));
		assertThat(output).doesNotContain("INSUFFICIENT_FUNDS").doesNotContain(failedEvent);
	}

	@Test
	void failedWebhookWritesFailureCodeAndPaymentFailedEvent() throws Exception {
		Payment payment = newPayment();

		send(failed(newEventId(), payment, "CARD_DECLINED")).andExpect(status().isNoContent());

		assertThat(paymentRow(payment.getId())).containsEntry("status", "failed")
			.containsEntry("failure_code", "CARD_DECLINED");
		assertThat(providerEventRows()).extracting(row -> row.get("event_type")).containsExactly("payment.failed");
		assertThat(outboxTypes(payment.getId())).containsExactly("PaymentFailed");
		String payload = jdbc.queryForObject("SELECT payload FROM outbox", String.class);
		assertThat(jsonMapper.readTree(payload).get("failureCode").asString()).isEqualTo("CARD_DECLINED");
	}

	@Test
	void unknownFieldsAreIgnored() throws Exception {
		Payment payment = newPayment();
		String body = succeeded(newEventId(), payment).replaceFirst("\\{",
				"{\"livemode\":false,\"metadata\":{\"a\":[1,2]},\"paymentId\":\"" + UUID.randomUUID() + "\",");

		send(body).andExpect(status().isNoContent());

		assertThat(paymentRow(payment.getId())).containsEntry("status", "succeeded");
	}

	@Test
	void jsonContentTypeWithCharsetAndEquivalentAmountAreAccepted() throws Exception {
		Payment payment = newPayment("150.00", "TRY");
		byte[] body = body(newEventId(), payment.getProviderPaymentId(), "payment.succeeded", "150.00", "TRY", null)
			.getBytes(StandardCharsets.UTF_8);

		mockMvc.perform(signed(MOCK_WEBHOOK, body, Instant.now().getEpochSecond())
			.contentType("application/json;charset=UTF-8")).andExpect(status().isNoContent());

		assertThat(paymentRow(payment.getId())).containsEntry("status", "succeeded");
	}

	// --- iş kuralı retleri ---

	@Test
	void unknownProviderPaymentIdIsUnknownPaymentAndRecordsNothing() throws Exception {
		Payment payment = newPayment();
		Payment withoutReference = payments.saveAndFlush(Payment.initiate(UUID.randomUUID(), UUID.randomUUID(),
				new BigDecimal(AMOUNT), "TRY", PaymentProviderType.MOCK, clock));

		for (String reference : List.of("mock_" + UUID.randomUUID(), payment.getProviderPaymentId().toUpperCase(),
				payment.getProviderPaymentId() + "x")) {
			send(body(newEventId(), reference, "payment.succeeded", AMOUNT, "TRY", null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNKNOWN_PAYMENT"))
				.andExpect(jsonPath("$.instance").value(MOCK_WEBHOOK));
		}

		assertThat(providerEventCount()).isZero();
		assertThat(outboxCount()).isZero();
		assertThat(paymentRow(payment.getId())).containsEntry("status", "initiated");
		assertThat(paymentRow(withoutReference.getId())).containsEntry("status", "initiated");
	}

	@Test
	void amountOrCurrencyMismatchIsRejectedAndChangesNothing() throws Exception {
		Payment payment = newPayment();
		Map<String, Object> before = paymentRow(payment.getId());

		for (String body : List.of(
				body(newEventId(), payment.getProviderPaymentId(), "payment.succeeded", "149.98", "TRY", null),
				body(newEventId(), payment.getProviderPaymentId(), "payment.succeeded", "1499.90", "TRY", null),
				body(newEventId(), payment.getProviderPaymentId(), "payment.succeeded", AMOUNT, "USD", null),
				body(newEventId(), payment.getProviderPaymentId(), "payment.failed", "0.01", "TRY", "CARD_DECLINED"))) {
			MockHttpServletResponse response = send(body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"))
				.andReturn()
				.getResponse();
			assertThat(response.getContentAsString()).doesNotContain("149").doesNotContain("USD").doesNotContain("0.01");
		}

		assertThat(paymentRow(payment.getId())).isEqualTo(before);
		assertThat(providerEventCount()).isZero();
		assertThat(outboxCount()).isZero();
	}

	// --- imza ---

	@Test
	void invalidSignatureOrStaleTimestampIsUnauthorizedWithSignatureChallenge(CapturedOutput output) throws Exception {
		Payment payment = newPayment();
		Map<String, Object> before = paymentRow(payment.getId());
		byte[] body = succeeded(newEventId(), payment).getBytes(StandardCharsets.UTF_8);
		long now = Instant.now().getEpochSecond();
		MockWebhookSigner otherSecret = new MockWebhookSigner(InternalTestKeys.randomKey());

		List<MockHttpServletRequestBuilder> requests = new ArrayList<>(List.of(
				post(MOCK_WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body),
				signed(MOCK_WEBHOOK, body, now - 360),
				signed(MOCK_WEBHOOK, body, now + 360),
				post(MOCK_WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
					.header(MockWebhookSigner.TIMESTAMP_HEADER, Long.toString(now))
					.header(MockWebhookSigner.SIGNATURE_HEADER, "sha256=" + "0".repeat(64)),
				post(MOCK_WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
					.header(MockWebhookSigner.TIMESTAMP_HEADER, Long.toString(now))
					.header(MockWebhookSigner.SIGNATURE_HEADER, otherSecret.sign(now, body)),
				post(MOCK_WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
					.header(MockWebhookSigner.SIGNATURE_HEADER, signer.sign(now, body)),
				post(MOCK_WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
					.header(MockWebhookSigner.TIMESTAMP_HEADER, "now")
					.header(MockWebhookSigner.SIGNATURE_HEADER, signer.sign(now, body))));
		byte[] tampered = succeeded(newEventId(), payment).getBytes(StandardCharsets.UTF_8);
		requests.add(post(MOCK_WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(tampered)
			.header(MockWebhookSigner.TIMESTAMP_HEADER, Long.toString(now))
			.header(MockWebhookSigner.SIGNATURE_HEADER, signer.sign(now, body)));

		for (MockHttpServletRequestBuilder request : requests) {
			expectSignatureRejected(mockMvc.perform(request));
		}

		assertThat(paymentRow(payment.getId())).isEqualTo(before);
		assertThat(providerEventCount()).isZero();
		assertThat(outboxCount()).isZero();
		List<String> warnings = output.getAll().lines().filter(line -> line.contains("Rejected webhook")).toList();
		assertThat(warnings).hasSize(requests.size())
			.allSatisfy(line -> assertThat(line).contains(" WARN ").endsWith(SIGNATURE_WARNING));
	}

	/** İmza JSON'dan önce: bozuk JSON + bozuk imza 401 (400 değil), yani gövde parse edilmedi. */
	@Test
	void badSignatureWithBrokenJsonIsUnauthorizedNotBadRequest() throws Exception {
		long now = Instant.now().getEpochSecond();
		for (String broken : List.of("{not json", "", "[]", "{\"eventId\":")) {
			expectSignatureRejected(mockMvc.perform(post(MOCK_WEBHOOK).contentType(MediaType.APPLICATION_JSON)
				.content(broken)
				.header(MockWebhookSigner.TIMESTAMP_HEADER, Long.toString(now))
				.header(MockWebhookSigner.SIGNATURE_HEADER, "sha256=" + "a".repeat(64))));
		}
		send("{not json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	// --- yol, metot, gövde ---

	@Test
	void unknownOrInactiveProviderIsNotFoundBeforeTheBodyIsRead() throws Exception {
		Payment payment = newPayment();
		byte[] body = succeeded(newEventId(), payment).getBytes(StandardCharsets.UTF_8);
		long now = Instant.now().getEpochSecond();

		for (String path : List.of("/webhooks/stripe", "/webhooks/x", "/webhooks/iyzico", "/webhooks/MOCK")) {
			mockMvc.perform(signed(path, body, now))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.instance").value(path));
			// Gövde okunmadığı için sınırı aşan gövde 413 değil, tür kontrolü yapılmadığı için text/plain 415 değil.
			mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(new byte[70_000]))
				.andExpect(status().isNotFound());
			mockMvc.perform(post(path).contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isNotFound());
		}
		assertThat(paymentRow(payment.getId())).containsEntry("status", "initiated");
	}

	/** Webhook zinciri yalnızca POST /webhooks/* açar: diğer metotlar 403 (405 değil), challenge yok. */
	@Test
	void otherMethodsAndPathsAreForbidden() throws Exception {
		for (MockHttpServletRequestBuilder request : List.of(get(MOCK_WEBHOOK), put(MOCK_WEBHOOK), patch(MOCK_WEBHOOK),
				delete(MOCK_WEBHOOK), get("/webhooks/x"), post("/webhooks"), post("/webhooks/mock/extra"))) {
			mockMvc.perform(request)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
		}
	}

	@Test
	void bodyLargerThanLimitIsTooLargeAndBodyAtLimitIsAccepted() throws Exception {
		Payment payment = newPayment();
		String json = succeeded(newEventId(), payment);
		long now = Instant.now().getEpochSecond();
		byte[] atLimit = (json + " ".repeat(65536 - json.length())).getBytes(StandardCharsets.UTF_8);
		byte[] overLimit = (json + " ".repeat(65537 - json.length())).getBytes(StandardCharsets.UTF_8);
		assertThat(atLimit).hasSize(65536);

		mockMvc.perform(signed(MOCK_WEBHOOK, overLimit, now))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
		assertThat(paymentRow(payment.getId())).containsEntry("status", "initiated");

		mockMvc.perform(signed(MOCK_WEBHOOK, atLimit, now)).andExpect(status().isNoContent());
		assertThat(paymentRow(payment.getId())).containsEntry("status", "succeeded");
	}

	@Test
	void nonJsonContentTypeIsUnsupportedMediaType() throws Exception {
		Payment payment = newPayment();
		byte[] body = succeeded(newEventId(), payment).getBytes(StandardCharsets.UTF_8);
		long now = Instant.now().getEpochSecond();

		for (String contentType : List.of("text/plain", "application/x-www-form-urlencoded", "application/xml",
				"application/problem+json", "application/octet-stream")) {
			mockMvc.perform(signed(MOCK_WEBHOOK, body, now).contentType(contentType))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
		}
		mockMvc.perform(post(MOCK_WEBHOOK).content(body)
			.header(MockWebhookSigner.TIMESTAMP_HEADER, Long.toString(now))
			.header(MockWebhookSigner.SIGNATURE_HEADER, signer.sign(now, body)))
			.andExpect(status().isUnsupportedMediaType());
		assertThat(paymentRow(payment.getId())).containsEntry("status", "initiated");
	}

	// --- doğrulama (imza geçerli) ---

	@Test
	void invalidBodiesAreBadRequestEvenWhenSigned() throws Exception {
		Payment payment = newPayment();
		String ref = payment.getProviderPaymentId();
		Map<String, String> invalid = Map.ofEntries(
				Map.entry("failureCodeMatchesType", body(newEventId(), ref, "payment.failed", AMOUNT, "TRY", null)),
				Map.entry("failureCodeMatchesType#",
						body(newEventId(), ref, "payment.succeeded", AMOUNT, "TRY", "CARD_DECLINED")),
				Map.entry("amount", body(newEventId(), ref, "payment.succeeded", "149.9", "TRY", null)),
				Map.entry("amount#", body(newEventId(), ref, "payment.succeeded", "149.990", "TRY", null)),
				Map.entry("amount##", body(newEventId(), ref, "payment.succeeded", "-149.99", "TRY", null)),
				Map.entry("type", body(newEventId(), ref, "payment.refunded", AMOUNT, "TRY", null)),
				Map.entry("type#", body(newEventId(), ref, "PAYMENT.SUCCEEDED", AMOUNT, "TRY", null)),
				Map.entry("eventId", body("evt.1", ref, "payment.succeeded", AMOUNT, "TRY", null)),
				Map.entry("eventId#", body("evt 1", ref, "payment.succeeded", AMOUNT, "TRY", null)),
				Map.entry("eventId##", body("e".repeat(129), ref, "payment.succeeded", AMOUNT, "TRY", null)),
				Map.entry("eventId###", body(null, ref, "payment.succeeded", AMOUNT, "TRY", null)),
				Map.entry("providerPaymentId", body(newEventId(), null, "payment.succeeded", AMOUNT, "TRY", null)),
				Map.entry("currency", body(newEventId(), ref, "payment.succeeded", AMOUNT, "try", null)),
				Map.entry("failureCode", body(newEventId(), ref, "payment.failed", AMOUNT, "TRY", "card declined")));

		for (Map.Entry<String, String> entry : invalid.entrySet()) {
			String field = entry.getKey().replace("#", "");
			String response = send(entry.getValue()).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem(field)))
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(response).as(entry.getKey()).doesNotContain(ref).doesNotContain("evt").doesNotContain("149.9");
		}

		assertThat(paymentRow(payment.getId())).containsEntry("status", "initiated");
		assertThat(providerEventCount()).isZero();
		assertThat(outboxCount()).isZero();
	}

	@Test
	void unreadableBodiesAreMalformedEvenWhenSigned() throws Exception {
		Payment payment = newPayment();
		String ref = payment.getProviderPaymentId();
		for (String body : List.of(
				"{\"eventId\":\"evt_1\",\"providerPaymentId\":\"" + ref
						+ "\",\"type\":\"payment.succeeded\",\"amount\":149.99,\"currency\":\"TRY\"}",
				"{\"eventId\":12,\"providerPaymentId\":\"" + ref
						+ "\",\"type\":\"payment.succeeded\",\"amount\":\"149.99\",\"currency\":\"TRY\"}",
				succeeded(newEventId(), payment) + "{}",
				"{\"eventId\":\"evt_1\",\"eventId\":\"evt_2\"}", "null", "[]", "\"x\"", "{")) {
			send(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		}
		assertThat(paymentRow(payment.getId())).containsEntry("status", "initiated");
		assertThat(providerEventCount()).isZero();
	}

	// --- kimlik bilgisi karışmaz ---

	@Test
	void internalKeyMeansNothingOnWebhooksAndWebhookSignatureMeansNothingOnInternal() throws Exception {
		Payment payment = newPayment();
		byte[] body = succeeded(newEventId(), payment).getBytes(StandardCharsets.UTF_8);

		expectSignatureRejected(mockMvc.perform(post(MOCK_WEBHOOK).contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY)));
		expectSignatureRejected(mockMvc.perform(post(MOCK_WEBHOOK).contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + InternalTestKeys.ORDER_SERVICE_KEY)));

		String createBody = "{\"orderId\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID()
				+ "\",\"amount\":10.00,\"currency\":\"TRY\"}";
		mockMvc.perform(signed("/internal/payments", createBody.getBytes(StandardCharsets.UTF_8),
				Instant.now().getEpochSecond()))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, InternalApiKeyAuthenticationEntryPoint.CHALLENGE));

		assertThat(paymentRow(payment.getId())).containsEntry("status", "initiated");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isEqualTo(1);
	}

	// --- log ---

	@Test
	void logsContainNoBodySignatureTimestampSecretOrIds(CapturedOutput output) throws Exception {
		Payment payment = newPayment();
		Payment other = newPayment();
		String eventId = newEventId();
		String failedEventId = newEventId();
		String body = succeeded(eventId, payment);
		long timestamp = Instant.now().getEpochSecond();
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		String signature = signer.sign(timestamp, bytes);

		mockMvc.perform(signed(MOCK_WEBHOOK, bytes, timestamp)).andExpect(status().isNoContent());
		mockMvc.perform(signed(MOCK_WEBHOOK, bytes, timestamp)).andExpect(status().isNoContent());
		send(failed(failedEventId, other, "CARD_DECLINED")).andExpect(status().isNoContent());
		send(failed(newEventId(), payment, "EXPIRED_CARD")).andExpect(status().isNoContent());
		send(body(newEventId(), "mock_unknown_ref_1", "payment.succeeded", AMOUNT, "TRY", null))
			.andExpect(status().isBadRequest());
		send(body(newEventId(), other.getProviderPaymentId(), "payment.succeeded", "149.98", "TRY", null))
			.andExpect(status().isBadRequest());
		send(body(newEventId(), other.getProviderPaymentId(), "payment.succeeded", "149.9", "TRY", null))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post(MOCK_WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(bytes)
			.header(MockWebhookSigner.TIMESTAMP_HEADER, Long.toString(timestamp))
			.header(MockWebhookSigner.SIGNATURE_HEADER, "sha256=" + "0".repeat(64)))
			.andExpect(status().isUnauthorized());

		String outboxEventId = jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM outbox", String.class).get(0);
		for (String value : List.of(body, signature, signature.substring("sha256=".length()), Long.toString(timestamp),
				WebhookTestSecrets.MOCK_SECRET, eventId, failedEventId, payment.getProviderPaymentId(),
				other.getProviderPaymentId(), "mock_unknown_ref_1", payment.getId().toString(), other.getId().toString(),
				payment.getOrderId().toString(), outboxEventId, AMOUNT, "149.98", "149.9", "CARD_DECLINED",
				"EXPIRED_CARD", "providerPaymentId")) {
			assertThat(output).as(value).doesNotContain(value);
		}
		assertThat(output).contains("Webhook handled (provider=mock, type=payment.succeeded, outcome=APPLIED)")
			.contains("Webhook handled (provider=mock, type=payment.succeeded, outcome=DUPLICATE)")
			.contains("Webhook handled (provider=mock, type=payment.failed, outcome=APPLIED)")
			.contains("Webhook handled (provider=mock, type=payment.failed, outcome=CONFLICTING_FINAL)")
			.contains("POST /webhooks/mock -> UNKNOWN_PAYMENT")
			.contains("POST /webhooks/mock -> AMOUNT_MISMATCH")
			.contains("POST /webhooks/mock -> VALIDATION_FAILED")
			.contains(SIGNATURE_WARNING)
			.doesNotContain(" ERROR ");
	}

	private static void expectSignatureRejected(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, WebhookSignatureException.CHALLENGE))
			.andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"))
			.andExpect(jsonPath("$.detail").value("Webhook signature is missing or invalid."))
			.andExpect(jsonPath("$.instance").value(MOCK_WEBHOOK));
	}

}
