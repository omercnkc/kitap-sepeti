package com.kitapsepeti.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.payment.ApiTestSupport;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.entity.PaymentStatus;
import com.kitapsepeti.payment.exception.PaymentErrorCode;
import com.kitapsepeti.payment.provider.mock.MockWebhookSigner;
import com.kitapsepeti.payment.support.InternalTestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Doküman gerçek uçları birebir anlatır: yalnızca üç operasyon, yol önekine göre güvenlik şeması, her operasyonun gerçek
 * yanıt kodları, API_CODES sırasıyla Problem enum'u ve örnek isteklerin gerçek hata yanıtları dokümanla uyuşur.
 */
class OpenApiDocsTest extends ApiTestSupport {

	private static final String PROBLEM_JSON = "application/problem+json";

	private static final String CREATE = "POST /internal/payments";

	private static final String GET_PAYMENT = "GET /internal/payments/{paymentId}";

	private static final String WEBHOOK = "POST /webhooks/{provider}";

	@Autowired
	private MockWebhookSigner signer;

	@Autowired
	private Clock clock;

	@Test
	void apiDocsAndSwaggerUiAreServedAnonymously() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.openapi").value(startsWith("3.")));
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());

		MockHttpServletResponse redirect = mockMvc.perform(get("/swagger-ui.html")).andReturn().getResponse();
		if (redirect.getStatus() != 200) {
			assertThat(redirect.getStatus()).isBetween(300, 399);
			assertThat(redirect.getRedirectedUrl()).endsWith("/swagger-ui/index.html");
		}
	}

	/** Internal ve webhook zincirleri doküman yollarını eşlemez: anahtar ya da imza başlığı sonucu değiştirmez. */
	@Test
	void internalAndWebhookChainsDoNotAffectApiDocs() throws Exception {
		for (String key : List.of(InternalTestKeys.ORDER_SERVICE_KEY, InternalTestKeys.randomKey())) {
			mockMvc.perform(get("/v3/api-docs").header(InternalApiKeyAuthenticationFilter.HEADER, key))
				.andExpect(status().isOk());
			mockMvc.perform(get("/swagger-ui/index.html").header(InternalApiKeyAuthenticationFilter.HEADER, key))
				.andExpect(status().isOk());
		}
		mockMvc.perform(get("/v3/api-docs").header(MockWebhookSigner.SIGNATURE_HEADER, "sha256=00"))
			.andExpect(status().isOk());
	}

	@Test
	void documentsExactlyTheThreeRealOperations() throws Exception {
		assertThat(operations(docs()).keySet()).containsExactlyInAnyOrder(CREATE, GET_PAYMENT, WEBHOOK);
	}

	/** Actuator ve /error bağlamda var ama dokümana girmez; şemalar yalnızca bu üç operasyonunkiler. */
	@Test
	void actuatorErrorAndTestOnlyPathsAreNotDocumented() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

		DocumentContext docs = docs();
		Map<String, Object> paths = docs.read("$.paths");
		assertThat(paths.keySet())
			.noneMatch(path -> path.startsWith("/actuator") || path.startsWith("/error") || path.contains("_"))
			.containsExactlyInAnyOrder("/internal/payments", "/internal/payments/{paymentId}", "/webhooks/{provider}");
		Map<String, Object> schemas = docs.read("$.components.schemas");
		assertThat(schemas.keySet()).containsExactlyInAnyOrder("CreatePaymentRequest", "PaymentResponse",
				"PaymentStatus", "WebhookEvent", "Problem", "FieldError");
	}

	@Test
	void tagsAreSortedAndEveryOperationHasOne() throws Exception {
		DocumentContext docs = docs();
		assertThat(docs.<List<String>>read("$.tags[*].name")).containsExactly("Internal – Payments", "Webhooks");
		operations(docs).forEach((operation, spec) -> assertThat(spec.get("tags")).as(operation)
			.isEqualTo(List.of(operation.equals(WEBHOOK) ? "Webhooks" : "Internal – Payments")));
	}

	/** Herkese açık operasyon yok: global security tanımsız, her operasyon yol önekine göre tek şema taşır. */
	@Test
	void everyOperationHasExactlyTheSecuritySchemeOfItsPathPrefix() throws Exception {
		DocumentContext docs = docs();
		assertThat(docs.<Map<String, Object>>read("$")).doesNotContainKey("security");
		assertThat(docs.<Map<String, Object>>read("$.components.securitySchemes"))
			.containsOnlyKeys("internalApiKey", "mockWebhookSignature");
		assertThat(docs.<Map<String, Object>>read("$.components.securitySchemes.internalApiKey"))
			.containsEntry("type", "apiKey").containsEntry("in", "header").containsEntry("name", "X-Internal-Api-Key");
		assertThat(docs.<Map<String, Object>>read("$.components.securitySchemes.mockWebhookSignature"))
			.containsEntry("type", "apiKey").containsEntry("in", "header").containsEntry("name", "X-Mock-Signature");
		assertThat(docs.<String>read("$.components.securitySchemes.mockWebhookSignature.description"))
			.contains("`sha256=`", "HMAC-SHA256(secret, \"<timestamp>.<ham gövde>\")", "±5 dk", "X-Mock-Timestamp");

		operations(docs).forEach((operation, spec) -> {
			String scheme = path(operation).startsWith("/internal/") ? "internalApiKey" : "mockWebhookSignature";
			assertThat(spec.get("security")).as(operation).isEqualTo(List.of(Map.of(scheme, List.of())));
		});
		assertThat(header401Example(docs, CREATE)).isEqualTo("ApiKey realm=\"internal\"");
		assertThat(header401Example(docs, GET_PAYMENT)).isEqualTo("ApiKey realm=\"internal\"");
		assertThat(header401Example(docs, WEBHOOK)).isEqualTo("Signature realm=\"webhook\"");
	}

	/** Her operasyonun yanıt kodları tam olarak gerçek davranış; uydurma kod yok. */
	@Test
	void everyOperationDocumentsExactlyItsRealResponseCodes() throws Exception {
		DocumentContext docs = docs();
		Map<String, Map<String, Object>> operations = operations(docs);

		assertThat(responses(operations.get(CREATE))).containsOnlyKeys("200", "201", "400", "401", "409", "500", "503");
		assertThat(responses(operations.get(GET_PAYMENT))).containsOnlyKeys("200", "400", "401", "404", "500");
		assertThat(responses(operations.get(WEBHOOK))).containsOnlyKeys("204", "400", "401", "404", "413", "415", "500");

		assertThat(docs.<Map<String, Object>>read(responsePath(CREATE, "201") + ".headers")).containsOnlyKeys("Location");
		assertThat(docs.<Map<String, Object>>read(responsePath(WEBHOOK, "204"))).doesNotContainKey("content");
		assertThat(description(docs, CREATE, "409")).contains("`PAYMENT_ORDER_MISMATCH`");
		assertThat(description(docs, CREATE, "503")).contains("`PAYMENT_PROVIDER_UNAVAILABLE`");
		assertThat(description(docs, GET_PAYMENT, "404")).contains("`RESOURCE_NOT_FOUND`");
		assertThat(description(docs, WEBHOOK, "400")).contains("`VALIDATION_FAILED`", "`MALFORMED_REQUEST`",
				"`UNKNOWN_PAYMENT`", "`AMOUNT_MISMATCH`");
		assertThat(description(docs, WEBHOOK, "401")).contains("`WEBHOOK_SIGNATURE_INVALID`");
		assertThat(description(docs, WEBHOOK, "404")).contains("`NOT_FOUND`");
		assertThat(description(docs, WEBHOOK, "413")).contains("`PAYLOAD_TOO_LARGE`");
		assertThat(description(docs, WEBHOOK, "415")).contains("`UNSUPPORTED_MEDIA_TYPE`");
	}

	@Test
	void problemCodeEnumMatchesApiCodesInOrder() throws Exception {
		List<String> documented = docs().read("$.components.schemas.Problem.properties.code.enum");

		assertThat(documented)
			.containsExactlyElementsOf(PaymentErrorCode.API_CODES.stream().map(ErrorCode::name).toList());
		assertThat(PaymentErrorCode.API_CODES).doesNotHaveDuplicates();
	}

	@Test
	void everyErrorResponseIsProblemJsonOnly() throws Exception {
		operations(docs()).forEach((operation, spec) -> responses(spec).forEach((code, response) -> {
			if (code.startsWith("4") || code.startsWith("5")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> contentTypes = (Map<String, Object>) ((Map<String, Object>) response).get("content");
				assertThat(contentTypes).as("%s %s", operation, code).containsOnlyKeys(PROBLEM_JSON);
			}
		}));
	}

	@Test
	void webhookParametersAndBodyAreDocumented() throws Exception {
		DocumentContext docs = docs();
		List<Map<String, Object>> parameters = docs.read("$.paths['/webhooks/{provider}'].post.parameters");
		assertThat(parameters).extracting(parameter -> parameter.get("in") + ":" + parameter.get("name"))
			.containsExactlyInAnyOrder("path:provider", "header:X-Mock-Timestamp");

		Map<String, Object> provider = parameter(parameters, "provider");
		assertThat(provider).containsEntry("required", true);
		assertThat(provider.get("schema")).isEqualTo(Map.of("type", "string", "enum", List.of("mock")));
		Map<String, Object> timestamp = parameter(parameters, "X-Mock-Timestamp");
		assertThat(timestamp).containsEntry("required", true);
		assertThat(timestamp.get("schema")).isEqualTo(Map.of("type", "string", "pattern", "^[0-9]{1,18}$"));
		assertThat(parameters).noneMatch(parameter -> "X-Mock-Signature".equals(parameter.get("name")));

		assertThat(docs.<Boolean>read("$.paths['/webhooks/{provider}'].post.requestBody.required")).isTrue();
		assertThat(docs.<String>read(
				"$.paths['/webhooks/{provider}'].post.requestBody.content['application/json'].schema['$ref']"))
			.isEqualTo("#/components/schemas/WebhookEvent");

		Map<String, Object> amount = docs.read("$.components.schemas.WebhookEvent.properties.amount");
		assertThat(amount).containsEntry("type", "string").containsEntry("pattern", "^(0|[1-9][0-9]{0,9})\\.[0-9]{2}$");
		assertThat(docs.<List<String>>read("$.components.schemas.WebhookEvent.properties.type.enum"))
			.containsExactly("payment.succeeded", "payment.failed");
		assertThat(docs.<String>read("$.components.schemas.WebhookEvent.properties.failureCode.description"))
			.contains("`payment.failed`");
		assertThat(docs.<List<String>>read("$.components.schemas.WebhookEvent.required"))
			.containsExactlyInAnyOrder("eventId", "providerPaymentId", "type", "amount", "currency");
		assertThat(docs.<Map<String, Object>>read("$.components.schemas.WebhookEvent.properties"))
			.containsOnlyKeys("eventId", "providerPaymentId", "type", "amount", "currency", "failureCode");
	}

	@Test
	void paymentSchemasDocumentBoundsNullabilityAndEnums() throws Exception {
		DocumentContext docs = docs();

		Map<String, Object> amount = docs.read("$.components.schemas.CreatePaymentRequest.properties.amount");
		assertThat(amount).containsEntry("type", "number").containsEntry("minimum", 0.01).doesNotContainKey("format");
		assertThat((String) amount.get("description")).contains("2 ondalık basamak");
		assertThat(docs.<String>read("$.components.schemas.CreatePaymentRequest.properties.currency.pattern"))
			.isEqualTo("^[A-Z]{3}$");
		for (String uuid : List.of("orderId", "userId")) {
			assertThat(docs.<Map<String, Object>>read("$.components.schemas.CreatePaymentRequest.properties." + uuid))
				.as(uuid).containsEntry("type", "string").containsEntry("format", "uuid");
		}
		assertThat(docs.<List<String>>read("$.components.schemas.CreatePaymentRequest.required"))
			.containsExactlyInAnyOrder("orderId", "userId", "amount", "currency");

		Map<String, Object> response = docs.read("$.components.schemas.PaymentResponse.properties");
		assertThat(response).containsOnlyKeys("paymentId", "orderId", "status", "amount", "currency", "failureCode",
				"redirectUrl", "createdAt", "updatedAt");
		assertThat(docs.<List<String>>read("$.components.schemas.PaymentResponse.required"))
			.containsExactlyInAnyOrderElementsOf(response.keySet());
		assertThat(response.get("status")).isEqualTo(Map.of("$ref", OpenApiConfig.PAYMENT_STATUS_SCHEMA_REF));
		assertThat(docs.<String>read("$.components.schemas.PaymentResponse.properties.redirectUrl.description"))
			.contains("v1'de her zaman null");
		Map<String, Object> responseAmount = docs.read("$.components.schemas.PaymentResponse.properties.amount");
		assertThat(responseAmount).containsEntry("type", "number").doesNotContainKey("format");
		assertThat((String) responseAmount.get("description")).contains("2 ondalık basamak");

		assertThat(docs.<List<String>>read("$.components.schemas.PaymentStatus.enum"))
			.containsExactlyElementsOf(Arrays.stream(PaymentStatus.values()).map(PaymentStatus::dbValue).toList())
			.containsExactly("initiated", "succeeded", "failed");

		assertThat(nullableProperties(docs))
			.isEqualTo(Map.of("PaymentResponse", Set.of("failureCode", "redirectUrl")));
	}

	/** Örnek isteklerin gerçek hata yanıtı dokümandaki kod, açıklama, şema ve başlıkla uyuşur. */
	@Test
	void documentedErrorsMatchRealResponses() throws Exception {
		DocumentContext docs = docs();

		UUID orderId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		mockMvc.perform(create(orderId, userId, "149.90")).andExpect(status().isCreated());
		assertCode(assertDocumentedError(docs, CREATE, 409, create(orderId, userId, "150.00")),
				PaymentErrorCode.PAYMENT_ORDER_MISMATCH);
		assertCode(assertDocumentedError(docs, CREATE, 400, internal(post("/internal/payments"))
			.contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"%s\"}".formatted(orderId))),
				"VALIDATION_FAILED");
		assertCode(assertDocumentedError(docs, CREATE, 400, internal(post("/internal/payments"))
			.contentType(MediaType.APPLICATION_JSON).content("{")), "MALFORMED_REQUEST");
		doThrow(new IllegalStateException("provider down")).when(provider).create(any());
		assertCode(assertDocumentedError(docs, CREATE, 503, create(UUID.randomUUID(), userId, "10.00")),
				PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
		MockHttpServletResponse apiKey = assertDocumentedError(docs, CREATE, 401, post("/internal/payments")
			.contentType(MediaType.APPLICATION_JSON).content("{}"));
		assertThat(apiKey.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(header401Example(docs, CREATE));

		assertCode(assertDocumentedError(docs, GET_PAYMENT, 404,
				internal(get("/internal/payments/{id}", UUID.randomUUID()))), "RESOURCE_NOT_FOUND");
		assertCode(assertDocumentedError(docs, GET_PAYMENT, 400, internal(get("/internal/payments/{id}", "not-a-uuid"))),
				"MALFORMED_REQUEST");
		MockHttpServletResponse getApiKey = assertDocumentedError(docs, GET_PAYMENT, 401,
				get("/internal/payments/{id}", UUID.randomUUID()));
		assertThat(getApiKey.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(header401Example(docs, GET_PAYMENT));

		Payment payment = paymentWithReference("149.90");
		String valid = webhookBody(payment.getProviderPaymentId(), "149.90");
		assertCode(assertDocumentedError(docs, WEBHOOK, 404, signed("/webhooks/stripe", valid)), "NOT_FOUND");
		assertCode(assertDocumentedError(docs, WEBHOOK, 415, signed("/webhooks/mock", valid)
			.contentType(MediaType.TEXT_PLAIN)), "UNSUPPORTED_MEDIA_TYPE");
		assertCode(assertDocumentedError(docs, WEBHOOK, 413, post("/webhooks/mock").contentType(MediaType.APPLICATION_JSON)
			.content(new byte[65_537])), PaymentErrorCode.PAYLOAD_TOO_LARGE);
		MockHttpServletResponse signature = assertDocumentedError(docs, WEBHOOK, 401, post("/webhooks/mock")
			.contentType(MediaType.APPLICATION_JSON)
			.header(MockWebhookSigner.TIMESTAMP_HEADER, Long.toString(Instant.now().getEpochSecond()))
			.header(MockWebhookSigner.SIGNATURE_HEADER, "sha256=" + "0".repeat(64))
			.content(valid));
		assertThat(signature.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(header401Example(docs, WEBHOOK));
		assertCode(assertDocumentedError(docs, WEBHOOK, 400, signed("/webhooks/mock", "{\"eventId\":")),
				"MALFORMED_REQUEST");
		assertCode(assertDocumentedError(docs, WEBHOOK, 400, signed("/webhooks/mock", "{}")), "VALIDATION_FAILED");
		assertCode(assertDocumentedError(docs, WEBHOOK, 400, signed("/webhooks/mock", webhookBody("mock_unknown", "149.90"))),
				PaymentErrorCode.UNKNOWN_PAYMENT);
		assertCode(assertDocumentedError(docs, WEBHOOK, 400,
				signed("/webhooks/mock", webhookBody(payment.getProviderPaymentId(), "149.91"))),
				PaymentErrorCode.AMOUNT_MISMATCH);

		mockMvc.perform(signed("/webhooks/mock", valid)).andExpect(status().isNoContent())
			.andExpect(content().string(""));
	}

	private static void assertCode(MockHttpServletResponse response, PaymentErrorCode code) throws Exception {
		assertCode(response, code.name());
	}

	private static void assertCode(MockHttpServletResponse response, String code) throws Exception {
		assertThat(JsonPath.<String>read(response.getContentAsString(StandardCharsets.UTF_8), "$.code")).isEqualTo(code);
	}

	/**
	 * Durum kodu operasyonda belgeli, gövde problem+json, {@code code} o yanıtın açıklamasında ve enum'da geçer,
	 * Problem'in required alanları var ve gövdede Problem şemasında olmayan alan yok.
	 */
	private MockHttpServletResponse assertDocumentedError(DocumentContext docs, String operation, int status,
			MockHttpServletRequestBuilder request) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
		String body = response.getContentAsString(StandardCharsets.UTF_8);
		assertThat(response.getStatus()).as("%s: %s", operation, body).isEqualTo(status);
		assertThat(response.getContentType()).as(operation).startsWith(PROBLEM_JSON);

		Map<String, Object> problem = JsonPath.parse(body).read("$");
		String code = (String) problem.get("code");
		assertThat(description(docs, operation, String.valueOf(status))).as(operation).contains("`" + code + "`");
		assertThat(docs.<List<String>>read("$.components.schemas.Problem.properties.code.enum")).contains(code);
		List<String> required = docs.read("$.components.schemas.Problem.required");
		assertThat(problem).as(operation).containsKeys(required.toArray(String[]::new));
		Set<String> documented = new HashSet<>(
				docs.<Map<String, Object>>read("$.components.schemas.Problem.properties").keySet());
		assertThat(documented).as(operation).containsAll(problem.keySet());
		return response;
	}

	private static MockHttpServletRequestBuilder internal(MockHttpServletRequestBuilder request) {
		return request.header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY);
	}

	private static MockHttpServletRequestBuilder create(UUID orderId, UUID userId, String amount) {
		return internal(post("/internal/payments")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":%s,\"currency\":\"TRY\"}".formatted(orderId,
					userId, amount));
	}

	private Payment paymentWithReference(String amount) {
		Payment payment = Payment.initiate(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(amount), "TRY",
				PaymentProviderType.MOCK, clock);
		payment.attachProviderReference("mock_" + UUID.randomUUID(), clock);
		return payments.saveAndFlush(payment);
	}

	private static String webhookBody(String providerPaymentId, String amount) {
		return """
				{"eventId":"evt_%s","providerPaymentId":"%s","type":"payment.succeeded","amount":"%s","currency":"TRY"}"""
			.formatted(UUID.randomUUID().toString().replace("-", ""), providerPaymentId, amount);
	}

	private MockHttpServletRequestBuilder signed(String path, String body) {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		long timestamp = Instant.now().getEpochSecond();
		return post(path).contentType(MediaType.APPLICATION_JSON)
			.header(MockWebhookSigner.TIMESTAMP_HEADER, Long.toString(timestamp))
			.header(MockWebhookSigner.SIGNATURE_HEADER, signer.sign(timestamp, bytes))
			.content(bytes);
	}

	private static Map<String, Object> parameter(List<Map<String, Object>> parameters, String name) {
		return parameters.stream().filter(parameter -> name.equals(parameter.get("name"))).findFirst().orElseThrow();
	}

	private static Map<String, Set<String>> nullableProperties(DocumentContext docs) {
		Map<String, Map<String, Object>> schemas = docs.read("$.components.schemas");
		Map<String, Set<String>> nullable = new TreeMap<>();
		schemas.forEach((name, schema) -> {
			@SuppressWarnings("unchecked")
			Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) schema.get("properties");
			if (properties != null) {
				properties.forEach((property, spec) -> {
					if (spec.get("type") instanceof List<?> types && types.contains("null")) {
						nullable.computeIfAbsent(name, key -> new HashSet<>()).add(property);
					}
				});
			}
		});
		return nullable;
	}

	private static String header401Example(DocumentContext docs, String operation) {
		return docs.read(responsePath(operation, "401") + ".headers['WWW-Authenticate'].schema.example");
	}

	private static String description(DocumentContext docs, String operation, String code) {
		return docs.read(responsePath(operation, code) + ".description");
	}

	private static String responsePath(String operation, String code) {
		String method = operation.substring(0, operation.indexOf(' ')).toLowerCase();
		return "$.paths['%s'].%s.responses['%s']".formatted(path(operation), method, code);
	}

	private static String path(String operation) {
		return operation.substring(operation.indexOf(' ') + 1);
	}

	/** "METHOD /yol" → operasyon. */
	private static Map<String, Map<String, Object>> operations(DocumentContext docs) {
		Map<String, Map<String, Map<String, Object>>> paths = docs.read("$.paths");
		Map<String, Map<String, Object>> operations = new TreeMap<>();
		paths.forEach((path, item) -> item.forEach((method, spec) -> operations.put(method.toUpperCase() + " " + path, spec)));
		return operations;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> responses(Map<String, Object> operation) {
		return (Map<String, Object>) operation.get("responses");
	}

	private DocumentContext docs() throws Exception {
		return JsonPath.parse(mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

}
