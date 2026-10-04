package com.kitapsepeti.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.payment.ApiTestSupport;
import com.kitapsepeti.payment.service.PaymentResults;
import com.kitapsepeti.payment.support.InternalTestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dokümandaki şemalar gerçek yanıtlarla tutarlı olmalı (cart ile aynı denetim): her örnek yanıt, o operasyonun ve
 * durum kodunun dokümanda yazan şemasına karşı denetlenir. Required alan yanıtta bulunur; null gelen alan şemada
 * nullable'dır ({@code type} dizisinde {@code "null"}); yanıtta şemada olmayan alan yoktur; değerin JSON tipi ve
 * enum'u şemayla aynıdır. Örnekler 201/200 POST ile initiated, succeeded ve failed ödeme GET'lerini kapsar; 2xx
 * yanıtlardan ulaşılabilen her şema denetlenmiş ve her nullable alan en az bir örnekte null görülmüş olmalı.
 */
class OpenApiRequiredFieldsTest extends ApiTestSupport {

	private static final String SCHEMA_PREFIX = "#/components/schemas/";

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private PaymentResults results;

	private JsonNode docs;

	private final Set<String> checkedSchemas = new TreeSet<>();

	private final Set<String> observedNulls = new TreeSet<>();

	private final List<String> violations = new ArrayList<>();

	@Test
	void realResponsesMatchRequiredNullableAndDeclaredFields() throws Exception {
		docs = jsonMapper.readTree(mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse()
			.getContentAsString(StandardCharsets.UTF_8));

		UUID orderId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		JsonNode created = check("post", "/internal/payments", 201, create(orderId, userId, "149.90"));
		assertThat(created.get("status").asString()).isEqualTo("initiated");
		JsonNode repeated = check("post", "/internal/payments", 200, create(orderId, userId, "149.90"));
		assertThat(repeated.get("paymentId")).isEqualTo(created.get("paymentId"));

		UUID initiated = UUID.fromString(created.get("paymentId").asString());
		JsonNode initiatedView = check("get", "/internal/payments/{paymentId}", 200, getPayment(initiated));
		assertThat(initiatedView.get("failureCode").isNull()).isTrue();
		assertThat(initiatedView.get("redirectUrl").isNull()).isTrue();

		UUID succeeded = paymentId(check("post", "/internal/payments", 201,
				create(UUID.randomUUID(), userId, "20.00")));
		results.recordSucceeded(succeeded);
		JsonNode succeededView = check("get", "/internal/payments/{paymentId}", 200, getPayment(succeeded));
		assertThat(succeededView.get("status").asString()).isEqualTo("succeeded");
		assertThat(succeededView.get("failureCode").isNull()).isTrue();

		UUID failed = paymentId(check("post", "/internal/payments", 201, create(UUID.randomUUID(), userId, "20.99")));
		results.recordFailed(failed, "CARD_DECLINED");
		JsonNode failedView = check("get", "/internal/payments/{paymentId}", 200, getPayment(failed));
		assertThat(failedView.get("status").asString()).isEqualTo("failed");
		assertThat(failedView.get("failureCode").asString()).isEqualTo("CARD_DECLINED");
		assertThat(failedView.get("redirectUrl").isNull()).isTrue();
		JsonNode failedRepeat = check("post", "/internal/payments", 200, create(UUID.fromString(
				failedView.get("orderId").asString()), userId, "20.99"));
		assertThat(failedRepeat.get("failureCode").asString()).isEqualTo("CARD_DECLINED");

		assertThat(violations).as("şemayla uyuşmayan alanlar").isEmpty();
		assertThat(checkedSchemas).as("örnek yanıtla denetlenen şemalar").containsAll(successResponseSchemas());
		assertThat(observedNulls).as("örneklerde null görülen alanlar = şemadaki nullable alanlar")
			.isEqualTo(nullableProperties(successResponseSchemas()));
	}

	private static UUID paymentId(JsonNode response) {
		return UUID.fromString(response.get("paymentId").asString());
	}

	private static MockHttpServletRequestBuilder create(UUID orderId, UUID userId, String amount) {
		return post("/internal/payments")
			.header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":%s,\"currency\":\"TRY\"}".formatted(orderId,
					userId, amount));
	}

	private static MockHttpServletRequestBuilder getPayment(UUID paymentId) {
		return get("/internal/payments/{id}", paymentId)
			.header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY);
	}

	/** İsteği atar, durum kodunu doğrular ve yanıtı dokümandaki o kodun şemasına karşı denetler. */
	private JsonNode check(String method, String path, int status, MockHttpServletRequestBuilder request)
			throws Exception {
		MockHttpServletResponse result = mockMvc.perform(request).andReturn().getResponse();
		String body = result.getContentAsString(StandardCharsets.UTF_8);
		assertThat(result.getStatus()).as("%s %s: %s", method, path, body).isEqualTo(status);
		JsonNode response = jsonMapper.readTree(body);
		JsonNode schema = docs.path("paths").path(path).path(method).path("responses").path(String.valueOf(status))
			.path("content").path("application/json").path("schema");
		assertThat(schema.isMissingNode()).as("%s %s %d dokümanda yok", method, path, status).isFalse();
		assertThat(response.isMissingNode() || response.isNull()).as("%s %s yanıtı: %s", method, path, body).isFalse();
		validate(response, schema, method.toUpperCase() + " " + path + " " + status);
		return response;
	}

	private void validate(JsonNode node, JsonNode schema, String location) {
		if (schema.has("$ref")) {
			String name = schemaName(schema);
			checkedSchemas.add(name);
			validateNamed(node, name, location + " <" + name + ">");
			return;
		}
		checkType(node, schema, location);
		if (schema.has("items")) {
			for (int i = 0; i < node.size(); i++) {
				validate(node.get(i), schema.get("items"), location + "[" + i + "]");
			}
		}
	}

	private void validateNamed(JsonNode node, String name, String location) {
		JsonNode schema = docs.path("components").path("schemas").path(name);
		checkType(node, schema, location);
		if (!schema.has("properties")) {
			return;
		}
		for (JsonNode required : schema.path("required")) {
			if (!node.has(required.asString())) {
				violations.add(location + "." + required.asString() + " (required ama yanıtta yok)");
			}
		}
		for (String field : node.propertyNames()) {
			JsonNode property = schema.path("properties").path(field);
			JsonNode value = node.get(field);
			if (property.isMissingNode()) {
				violations.add(location + "." + field + " (yanıtta var, şemada yok)");
			}
			else if (value.isNull()) {
				if (allowsNull(property)) {
					observedNulls.add(name + "." + field);
				}
				else {
					violations.add(location + "." + field + " (null geldi, şemada nullable değil)");
				}
			}
			else {
				validate(value, property, location + "." + field);
			}
		}
	}

	/** Şemadaki tip (nullable ise null dışındaki) ile JSON değerinin tipi ve varsa enum uyuşmalı. */
	private void checkType(JsonNode node, JsonNode schema, String location) {
		List<String> types = new ArrayList<>();
		JsonNode type = schema.path("type");
		if (type.isArray()) {
			type.forEach(item -> types.add(item.asString()));
		}
		else if (!type.isMissingNode()) {
			types.add(type.asString());
		}
		types.remove("null");
		boolean matches = types.isEmpty() || types.stream().anyMatch(expected -> switch (expected) {
			case "object" -> node.isObject();
			case "array" -> node.isArray();
			case "string" -> node.isString();
			case "integer" -> node.isIntegralNumber();
			case "number" -> node.isNumber();
			case "boolean" -> node.isBoolean();
			default -> false;
		});
		if (!matches) {
			violations.add(location + " (tip " + types + " bekleniyordu: " + node + ")");
		}
		if (schema.has("enum") && node.isString()) {
			Set<String> allowed = new TreeSet<>();
			schema.get("enum").forEach(item -> allowed.add(item.asString()));
			if (!allowed.contains(node.asString())) {
				violations.add(location + " (enum dışı: " + node + ")");
			}
		}
	}

	private static boolean allowsNull(JsonNode property) {
		JsonNode type = property.path("type");
		if (type.isArray()) {
			for (JsonNode item : type) {
				if ("null".equals(item.asString())) {
					return true;
				}
			}
		}
		return false;
	}

	private Set<String> nullableProperties(Set<String> schemaNames) {
		Set<String> nullable = new TreeSet<>();
		for (String name : schemaNames) {
			for (Map.Entry<String, JsonNode> property : docs.path("components").path("schemas").path(name)
				.path("properties").properties()) {
				if (allowsNull(property.getValue())) {
					nullable.add(name + "." + property.getKey());
				}
			}
		}
		return nullable;
	}

	/** 2xx yanıtların {@code application/json} şemalarından (iç içe referanslar dahil) ulaşılan şema adları. */
	private Set<String> successResponseSchemas() {
		Set<String> names = new TreeSet<>();
		for (JsonNode item : docs.path("paths")) {
			for (JsonNode operation : item) {
				for (Map.Entry<String, JsonNode> response : operation.path("responses").properties()) {
					if (response.getKey().startsWith("2")) {
						collectRefs(response.getValue().path("content").path("application/json").path("schema"), names);
					}
				}
			}
		}
		return names;
	}

	private void collectRefs(JsonNode schema, Set<String> names) {
		if (schema.isMissingNode()) {
			return;
		}
		if (schema.has("$ref")) {
			String name = schemaName(schema);
			if (names.add(name)) {
				collectRefs(docs.path("components").path("schemas").path(name), names);
			}
			return;
		}
		collectRefs(schema.path("items"), names);
		for (JsonNode property : schema.path("properties")) {
			collectRefs(property, names);
		}
	}

	private static String schemaName(JsonNode schema) {
		return schema.get("$ref").asString().substring(SCHEMA_PREFIX.length());
	}

}
