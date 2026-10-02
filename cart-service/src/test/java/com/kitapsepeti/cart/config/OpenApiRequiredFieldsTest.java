package com.kitapsepeti.cart.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.support.CatalogStub.Response;
import com.kitapsepeti.cart.support.FakeCatalog;
import com.kitapsepeti.cart.support.FakeCatalog.Book;
import com.kitapsepeti.cart.support.InternalTestKeys;
import com.kitapsepeti.cart.support.TestJwt;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dokümandaki şemalar gerçek yanıtlarla tutarlı olmalı: her örnek yanıt, o operasyonun ve durum kodunun dokümanda
 * yazan şemasına karşı (iç içe şemalar dahil) denetlenir. Required alan yanıtta bulunur; null gelen alan şemada
 * nullable'dır ({@code type} dizisinde {@code "null"}); yanıtta şemada olmayan alan yoktur; değerin JSON tipi şemayla
 * aynıdır. Örnekler dolu/boş sepet, Catalog kesintisi, farklı para birimleri ve dolu/boş anlık görüntüyü kapsar;
 * 2xx yanıtlardan ulaşılabilen her şema denetlenmiş ve her nullable alan en az bir örnekte null görülmüş olmalı.
 */
class OpenApiRequiredFieldsTest extends ApiTestSupport {

	private static final String SCHEMA_PREFIX = "#/components/schemas/";

	@Autowired
	private JsonMapper jsonMapper;

	private final FakeCatalog catalog = new FakeCatalog();

	private JsonNode docs;

	private final Set<String> checkedSchemas = new TreeSet<>();

	private final Set<String> observedNulls = new TreeSet<>();

	private final List<String> violations = new ArrayList<>();

	@Test
	void realResponsesMatchRequiredNullableAndDeclaredFields() throws Exception {
		CATALOG.respondWith(catalog);
		docs = jsonMapper.readTree(mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse()
			.getContentAsString(StandardCharsets.UTF_8));

		// Boş sepet (sepet hiç yok): currency null, subtotal 0.00; anlık görüntüde cartId/updatedAt null.
		String emptyUser = UUID.randomUUID().toString();
		check("get", "/api/cart", user(get("/api/cart"), emptyUser));
		check("delete", "/api/cart/items/{bookId}", user(delete("/api/cart/items/{id}", UUID.randomUUID()), emptyUser));
		check("delete", "/api/cart/items", user(delete("/api/cart/items"), emptyUser));
		check("post", "/internal/cart/snapshot", snapshot(emptyUser));

		// Dolu sepet, Catalog ayakta: kapaklı ve kapaksız satır, satıştan kalkmış kitap (available false,
		// currentUnitPrice null).
		String fullUser = UUID.randomUUID().toString();
		Book withCover = catalog.put(new Book(UUID.randomUUID(), "Kapaklı", "120.50", "TRY",
				"https://cdn.example.com/k.jpg", true));
		Book withoutCover = catalog.publish("Kapaksız", "45.00");
		Book withdrawn = catalog.publish("Kalkacak", "30.00");
		check("post", "/api/cart/items", user(post("/api/cart/items"), fullUser)
			.content("{\"bookId\":\"%s\",\"quantity\":2}".formatted(withCover.id())));
		check("post", "/api/cart/items", user(post("/api/cart/items"), fullUser)
			.content("{\"bookId\":\"%s\"}".formatted(withoutCover.id())));
		check("post", "/api/cart/items", user(post("/api/cart/items"), fullUser)
			.content("{\"bookId\":\"%s\"}".formatted(withdrawn.id())));
		catalog.unpublish(withdrawn.id());
		check("patch", "/api/cart/items/{bookId}", user(patch("/api/cart/items/{id}", withoutCover.id()), fullUser)
			.content("{\"quantity\":3}"));
		JsonNode full = check("get", "/api/cart", user(get("/api/cart"), fullUser));
		assertThat(full.get("catalogStatus").asString()).isEqualTo("VERIFIED");
		assertThat(full.get("currency").asString()).isEqualTo("TRY");
		JsonNode fullSnapshot = check("post", "/internal/cart/snapshot", snapshot(fullUser));
		assertThat(fullSnapshot.get("items").size()).isEqualTo(3);

		// Catalog kapalı: UNAVAILABLE, satırlarda available/currentUnitPrice null.
		catalog.failWith(Response.problem(503));
		JsonNode unavailable = check("get", "/api/cart", user(get("/api/cart"), fullUser));
		assertThat(unavailable.get("catalogStatus").asString()).isEqualTo("UNAVAILABLE");
		check("delete", "/api/cart/items/{bookId}", user(delete("/api/cart/items/{id}", withdrawn.id()), fullUser));
		catalog.recover();

		// Farklı para birimleri: currency ve subtotal null.
		String mixedUser = UUID.randomUUID().toString();
		Book lira = catalog.publish("Lira", "10.00");
		Book euro = catalog.put(new Book(UUID.randomUUID(), "Euro", "3.25", "EUR", null, true));
		check("post", "/api/cart/items", user(post("/api/cart/items"), mixedUser)
			.content("{\"bookId\":\"%s\"}".formatted(lira.id())));
		JsonNode mixed = check("post", "/api/cart/items", user(post("/api/cart/items"), mixedUser)
			.content("{\"bookId\":\"%s\"}".formatted(euro.id())));
		assertThat(mixed.get("currency").isNull()).isTrue();
		assertThat(mixed.get("subtotal").isNull()).isTrue();

		// Aktif ama boş sepet: anlık görüntüde cartId dolu, items boş.
		check("delete", "/api/cart/items", user(delete("/api/cart/items"), mixedUser));
		JsonNode emptiedSnapshot = check("post", "/internal/cart/snapshot", snapshot(mixedUser));
		assertThat(emptiedSnapshot.get("cartId").isNull()).isFalse();
		assertThat(emptiedSnapshot.get("items").isEmpty()).isTrue();

		assertThat(violations).as("şemayla uyuşmayan alanlar").isEmpty();
		assertThat(checkedSchemas).as("örnek yanıtla denetlenen şemalar").containsAll(successResponseSchemas());
		assertThat(observedNulls).as("örneklerde null görülen alanlar = şemadaki nullable alanlar")
			.isEqualTo(nullableProperties(successResponseSchemas()));
	}

	private MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request, String subject) {
		return request.with(bearer(TestJwt.user(subject))).contentType(MediaType.APPLICATION_JSON);
	}

	private static MockHttpServletRequestBuilder snapshot(String userId) {
		return post("/internal/cart/snapshot")
			.header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\":\"%s\"}".formatted(userId));
	}

	/** İsteği atar, 200 doğrular ve yanıtı dokümandaki şemaya karşı denetler. */
	private JsonNode check(String method, String path, MockHttpServletRequestBuilder request) throws Exception {
		MockHttpServletResponse result = mockMvc.perform(request).andReturn().getResponse();
		String body = result.getContentAsString(StandardCharsets.UTF_8);
		assertThat(result.getStatus()).as("%s %s: %s", method, path, body).isEqualTo(200);
		JsonNode response = jsonMapper.readTree(body);
		JsonNode schema = docs.path("paths").path(path).path(method).path("responses").path("200")
			.path("content").path("application/json").path("schema");
		assertThat(schema.isMissingNode()).as("%s %s 200 dokümanda yok", method, path).isFalse();
		assertThat(response.isMissingNode() || response.isNull()).as("%s %s yanıtı: %s", method, path, body).isFalse();
		validate(response, schema, method.toUpperCase() + " " + path);
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
