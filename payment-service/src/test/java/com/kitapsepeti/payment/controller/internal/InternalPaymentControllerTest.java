package com.kitapsepeti.payment.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationEntryPoint;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.payment.ApiTestSupport;
import com.kitapsepeti.payment.provider.ProviderPaymentRequest;
import com.kitapsepeti.payment.support.InternalTestKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code POST /internal/payments} ve {@code GET /internal/payments/{id}}: akış, idempotentlik, hata ve log hijyeni. */
@ExtendWith(OutputCaptureExtension.class)
class InternalPaymentControllerTest extends ApiTestSupport {

	private static final String PAYMENTS = "/internal/payments";

	private static final String MASKED = PAYMENTS + "/:paymentId";

	private final UUID orderId = UUID.randomUUID();

	private final UUID userId = UUID.randomUUID();

	// --- POST ---

	@Test
	void newPaymentIsCreatedAttachedToProviderAndReturned201() throws Exception {
		String body = create(orderId, userId, "149.90", "TRY")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderId").value(orderId.toString()))
			.andExpect(jsonPath("$.status").value("initiated"))
			.andExpect(jsonPath("$.currency").value("TRY"))
			.andExpect(jsonPath("$.failureCode").value(nullValue()))
			.andExpect(jsonPath("$.redirectUrl").value(nullValue()))
			.andExpect(jsonPath("$.createdAt").isString())
			.andExpect(jsonPath("$.updatedAt").isString())
			.andReturn().getResponse().getContentAsString();
		String paymentId = JsonPath.read(body, "$.paymentId");

		assertThat(body).contains("\"amount\":149.90").doesNotContain("userId").doesNotContain(userId.toString())
			.doesNotContain("mock_");
		Map<String, Object> row = row(orderId);
		assertThat(row).containsEntry("id", paymentId).containsEntry("s", "initiated").containsEntry("a", "149.90");
		assertThat((String) row.get("ref")).startsWith("mock_");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isEqualTo(1);
		verify(provider, times(1)).create(any(ProviderPaymentRequest.class));
	}

	@Test
	void createdResponseHasLocationOfThePayment() throws Exception {
		var response = create(orderId, userId, "10.00", "TRY").andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.LOCATION, startsWith(PAYMENTS + "/")))
			.andReturn().getResponse();
		String paymentId = JsonPath.read(response.getContentAsString(), "$.paymentId");

		assertThat(response.getHeader(HttpHeaders.LOCATION)).endsWith(PAYMENTS + "/" + paymentId);
	}

	@Test
	void sameRequestAgainReturns200WithSamePaymentWithoutCallingProvider() throws Exception {
		String first = create(orderId, userId, "149.90", "TRY").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		Map<String, Object> before = row(orderId);

		String second = create(orderId, userId, "149.9", "TRY").andExpect(status().isOk())
			.andExpect(header().doesNotExist(HttpHeaders.LOCATION))
			.andReturn().getResponse().getContentAsString();

		assertThat((String) JsonPath.read(second, "$.paymentId")).isEqualTo(JsonPath.read(first, "$.paymentId"));
		assertThat(second).isEqualTo(first);
		assertThat(row(orderId)).isEqualTo(before);
		verify(provider, times(1)).create(any(ProviderPaymentRequest.class));
	}

	@Test
	void sameOrderWithDifferentAmountUserOrCurrencyIsMismatchAndChangesNothing() throws Exception {
		create(orderId, userId, "149.90", "TRY").andExpect(status().isCreated());
		Map<String, Object> before = row(orderId);

		for (ResultActions attempt : List.of(create(orderId, userId, "149.91", "TRY"),
				create(orderId, UUID.randomUUID(), "149.90", "TRY"), create(orderId, userId, "149.90", "EUR"))) {
			String body = attempt.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PAYMENT_ORDER_MISMATCH"))
				.andExpect(jsonPath("$.instance").value(PAYMENTS))
				.andReturn().getResponse().getContentAsString();
			assertThat(body).doesNotContain(orderId.toString()).doesNotContain("149.9");
		}
		assertThat(row(orderId)).isEqualTo(before);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isEqualTo(1);
		verify(provider, times(1)).create(any(ProviderPaymentRequest.class));
	}

	@Test
	void providerFailureIs503AndLeavesUnattachedPaymentThatIsCompletedOnRetry() throws Exception {
		doThrow(new IllegalStateException("provider down")).when(provider).create(any(ProviderPaymentRequest.class));

		create(orderId, userId, "25.00", "TRY")
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"))
			.andExpect(jsonPath("$.instance").value(PAYMENTS));
		assertThat(row(orderId)).containsEntry("s", "initiated").containsEntry("ref", null);

		doCallRealMethod().when(provider).create(any(ProviderPaymentRequest.class));
		create(orderId, userId, "25.00", "TRY")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("initiated"));

		assertThat((String) row(orderId).get("ref")).startsWith("mock_");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isEqualTo(1);
		verify(provider, times(2)).create(any(ProviderPaymentRequest.class));
	}

	@Test
	void finalizedPaymentIsReturnedAsIsWithoutCallingProvider() throws Exception {
		create(orderId, userId, "10.00", "TRY").andExpect(status().isCreated());
		jdbc.update("UPDATE payments SET status = 'succeeded' WHERE order_id = UUID_TO_BIN(?)", orderId.toString());
		UUID unattachedOrder = UUID.randomUUID();
		doThrow(new IllegalStateException("provider down")).when(provider).create(any(ProviderPaymentRequest.class));
		create(unattachedOrder, userId, "10.00", "TRY").andExpect(status().isServiceUnavailable());
		jdbc.update("UPDATE payments SET status = 'failed', failure_code = 'CARD_DECLINED' WHERE order_id = UUID_TO_BIN(?)",
				unattachedOrder.toString());

		create(orderId, userId, "10.00", "TRY").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("succeeded"));
		create(unattachedOrder, userId, "10.00", "TRY").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("failed"))
			.andExpect(jsonPath("$.failureCode").value("CARD_DECLINED"));

		assertThat(row(unattachedOrder)).containsEntry("ref", null);
		verify(provider, times(2)).create(any(ProviderPaymentRequest.class));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":0,\"currency\":\"TRY\"}",
			"{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":0.00,\"currency\":\"TRY\"}",
			"{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":-5,\"currency\":\"TRY\"}",
			"{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":10.001,\"currency\":\"TRY\"}",
			"{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":12345678901.00,\"currency\":\"TRY\"}",
			"{\"orderId\":\"%s\",\"userId\":\"%s\",\"currency\":\"TRY\"}",
			"{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":10.00,\"currency\":\"try\"}",
			"{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":10.00,\"currency\":\"TRYY\"}",
			"{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":10.00}",
			"{\"userId\":\"%2$s\",\"amount\":10.00,\"currency\":\"TRY\"}",
			"{\"orderId\":\"%s\",\"amount\":10.00,\"currency\":\"TRY\"}" })
	void invalidFieldIsValidationErrorWithoutEchoingValues(String template) throws Exception {
		String body = withKey(post(PAYMENTS).contentType(MediaType.APPLICATION_JSON)
			.content(template.formatted(orderId, userId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").isString())
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(orderId.toString()).doesNotContain(userId.toString())
			.doesNotContain("10.001").doesNotContain("12345678901").doesNotContain("\"try\"").doesNotContain("TRYY");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isZero();
		verify(provider, never()).create(any(ProviderPaymentRequest.class));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "{\"orderId\":", "{\"orderId\":\"siparis-degil\",\"userId\":\"%2$s\"}",
			"{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":\"on-lira\",\"currency\":\"TRY\"}", "[\"%s\"]" })
	void unreadableBodyIsMalformedRequestWithoutEchoingValues(String template) throws Exception {
		String body = withKey(post(PAYMENTS).contentType(MediaType.APPLICATION_JSON)
			.content(template.formatted(orderId, userId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(orderId.toString()).doesNotContain(userId.toString())
			.doesNotContain("siparis-degil").doesNotContain("on-lira");
	}

	// --- GET ---

	@Test
	void getReturnsThePayment() throws Exception {
		String created = create(orderId, userId, "75.50", "TRY").andReturn().getResponse().getContentAsString();
		String paymentId = JsonPath.read(created, "$.paymentId");

		String body = withKey(get(PAYMENTS + "/{id}", paymentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentId").value(paymentId))
			.andExpect(jsonPath("$.orderId").value(orderId.toString()))
			.andExpect(jsonPath("$.status").value("initiated"))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).isEqualTo(created).contains("\"amount\":75.50").doesNotContain(userId.toString());
	}

	@Test
	void unknownPaymentIs404WithMaskedInstance() throws Exception {
		UUID unknown = UUID.randomUUID();

		String body = withKey(get(PAYMENTS + "/{id}", unknown))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
			.andExpect(jsonPath("$.instance").value(MASKED))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(unknown.toString());
	}

	@Test
	void malformedPaymentIdIs400WithMaskedInstance() throws Exception {
		String body = withKey(get(PAYMENTS + "/odeme-degil"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
			.andExpect(jsonPath("$.instance").value(MASKED))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("odeme-degil");
	}

	@Test
	void getWithoutKeyIs401WithMaskedInstance() throws Exception {
		UUID paymentId = UUID.randomUUID();

		String body = mockMvc.perform(get(PAYMENTS + "/{id}", paymentId))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, InternalApiKeyAuthenticationEntryPoint.CHALLENGE))
			.andExpect(jsonPath("$.instance").value(MASKED))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(paymentId.toString());
	}

	// --- log hijyeni ---

	@Test
	void logsContainNoIdsReferenceAmountKeyOrHash(CapturedOutput output) throws Exception {
		String amount = "7654.32";
		String created = create(orderId, userId, amount, "TRY").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String paymentId = JsonPath.read(created, "$.paymentId");
		String reference = (String) row(orderId).get("ref");
		String wrongKey = InternalTestKeys.randomKey();

		create(orderId, userId, amount, "TRY").andExpect(status().isOk());
		create(orderId, userId, "7654.33", "TRY").andExpect(status().isConflict());
		withKey(get(PAYMENTS + "/{id}", paymentId)).andExpect(status().isOk());
		withKey(get(PAYMENTS + "/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
		withKey(get(PAYMENTS + "/" + orderId + "x")).andExpect(status().isBadRequest());
		mockMvc.perform(get(PAYMENTS + "/{id}", paymentId)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(PAYMENTS + "/{id}", paymentId).header(InternalApiKeyAuthenticationFilter.HEADER, wrongKey))
			.andExpect(status().isUnauthorized());
		withKey(post(PAYMENTS).contentType(MediaType.APPLICATION_JSON)
			.content("{\"orderId\":\"" + orderId + "\",\"userId\":\"" + userId + "\",\"amount\":7654.321,\"currency\":\"TRY\"}"))
			.andExpect(status().isBadRequest());
		UUID otherOrder = UUID.randomUUID();
		doThrow(new IllegalStateException("provider down for " + otherOrder)).when(provider)
			.create(any(ProviderPaymentRequest.class));
		create(otherOrder, userId, amount, "TRY").andExpect(status().isServiceUnavailable());
		mockMvc.perform(get("/webhooks/mock")).andExpect(status().isForbidden());

		for (String secret : List.of(orderId.toString(), otherOrder.toString(), userId.toString(), paymentId, reference,
				amount, "7654.33", "7654.321", InternalTestKeys.ORDER_SERVICE_KEY, InternalTestKeys.ORDER_SERVICE_KEY_SHA256,
				wrongKey, "mock_")) {
			assertThat(output).doesNotContain(secret);
		}
		assertThat(output).contains("Internal request POST /internal/payments client=order-service")
			.contains("Internal request GET /internal/payments/:paymentId client=order-service")
			.contains("Rejected internal request GET /internal/payments/:paymentId -> UNAUTHORIZED")
			.contains("GET /internal/payments/:paymentId -> RESOURCE_NOT_FOUND")
			.contains("GET /internal/payments/:paymentId -> MALFORMED_REQUEST")
			.contains("POST /internal/payments -> PAYMENT_ORDER_MISMATCH")
			.contains("POST /internal/payments -> VALIDATION_FAILED")
			.contains("POST /internal/payments -> PAYMENT_PROVIDER_UNAVAILABLE (cause=IllegalStateException)")
			.contains("GET /webhooks/mock -> FORBIDDEN")
			.doesNotContain(" ERROR ");
	}

	// --- yardımcılar ---

	private ResultActions create(UUID order, UUID user, String amount, String currency) throws Exception {
		return withKey(post(PAYMENTS).contentType(MediaType.APPLICATION_JSON)
			.content("{\"orderId\":\"" + order + "\",\"userId\":\"" + user + "\",\"amount\":" + amount
					+ ",\"currency\":\"" + currency + "\"}"));
	}

	private ResultActions withKey(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY));
	}

	private Map<String, Object> row(UUID order) {
		return jdbc.queryForMap("""
				SELECT BIN_TO_UUID(id) AS id, status AS s, provider_payment_id AS ref, CAST(amount AS CHAR) AS a,
					failure_code AS f, DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') AS updated
				FROM payments WHERE order_id = UUID_TO_BIN(?)
				""", order.toString());
	}

}
