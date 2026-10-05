package com.kitapsepeti.order.config;

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

import com.kitapsepeti.order.controller.CheckoutTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dokümandaki şemalar gerçek yanıtlarla tutarlı olmalı (cart ve payment ile aynı denetim):
 * her örnek yanıt dokümanda yazan şemasına karşı denetlenir. Required alan yanıtta bulunur;
 * null gelen alan şemada nullable'dır; yanıtta şemada olmayan alan yoktur; değerin JSON tipi
 * ve enum'u şemayla aynıdır. 2xx yanıtlardan ulaşılabilen her şema denetlenmiş ve her nullable
 * alan en az bir örnekte null görülmüş olmalı.
 */
class OpenApiRequiredFieldsTest extends CheckoutTestSupport {

	private static final String SCHEMA_PREFIX = "#/components/schemas/";

	@Autowired
	private JsonMapper jsonMapper;

	private JsonNode docs;

	private final Set<String> checkedSchemas = new TreeSet<>();

	private final Set<String> observedNulls = new TreeSet<>();

	private final List<String> violations = new ArrayList<>();

	@Test
	void realResponsesMatchRequiredNullableAndDeclaredFields() throws Exception {
		docs = jsonMapper.readTree(mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse()
			.getContentAsString(StandardCharsets.UTF_8));

		Book book = Book.of("Test Kitabi", "89.50");
		stubCart(new Line(book, 2));
		stubLookup(book);
		stubReserveHeld();
		stubPaymentInitiated(UUID.randomUUID(), "initiated");

		// line2, district ve postalCode null olan adres ile checkout
		String minimalAddress = """
				{"recipientName":"Ahmet Yilmaz","phone":"+905551112233","line1":"Gunes Sok. No:5",\
				"city":"Ankara","country":"TR"}""";
		String checkoutBody = "{\"address\":" + minimalAddress + "}";

		// 1. Checkout (201 Created) -> OrderResponse
		JsonNode createdOrder = check("post", "/api/orders/checkout", 201,
				post("/api/orders/checkout")
					.with(bearer(this.token))
					.contentType(MediaType.APPLICATION_JSON)
					.content(checkoutBody));

		UUID orderId = UUID.fromString(createdOrder.get("id").asString());
		assertThat(createdOrder.get("status").asString()).isEqualTo("pending");
		assertThat(createdOrder.get("failureCode").isNull()).isTrue();
		assertThat(createdOrder.get("address").get("line2").isNull()).isTrue();
		assertThat(createdOrder.get("address").get("district").isNull()).isTrue();
		assertThat(createdOrder.get("address").get("postalCode").isNull()).isTrue();

		// 2. Detay oku (200 OK) -> OrderResponse
		JsonNode orderDetail = check("get", "/api/orders/{orderId}", 200,
				get("/api/orders/{orderId}", orderId)
					.with(bearer(this.token))
					.accept(MediaType.APPLICATION_JSON));
		assertThat(orderDetail.get("id").asString()).isEqualTo(orderId.toString());

		// 3. Liste oku (200 OK) -> PageResponseOrderSummaryResponse
		JsonNode orderList = check("get", "/api/orders", 200,
				get("/api/orders?page=0&size=10")
					.with(bearer(this.token))
					.accept(MediaType.APPLICATION_JSON));
		assertThat(orderList.get("items").size()).isGreaterThanOrEqualTo(1);
		assertThat(orderList.get("items").get(0).get("failureCode").isNull()).isTrue();

		assertThat(violations).as("şemayla uyuşmayan alanlar").isEmpty();
		assertThat(checkedSchemas).as("örnek yanıtla denetlenen şemalar").containsAll(successResponseSchemas());
		assertThat(observedNulls).as("örneklerde null görülen alanlar = şemadaki nullable alanlar")
			.isEqualTo(nullableProperties(successResponseSchemas()));
	}

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
