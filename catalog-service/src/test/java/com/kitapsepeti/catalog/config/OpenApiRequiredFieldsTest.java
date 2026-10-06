package com.kitapsepeti.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.client.BookMetadataClient;
import com.kitapsepeti.catalog.dto.response.IsbnMetadataResponse;
import com.kitapsepeti.catalog.support.InternalTestKeys;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dokümandaki {@code required} listeleri gerçek yanıtlarla tutarlı olmalı: her örnek yanıt, o operasyonun ve durum
 * kodunun dokümanda yazan şemasına karşı (iç içe şemalar dahil) denetlenir; required alan eksik ya da null olamaz.
 * 2xx yanıtlardan ulaşılabilen her şema en az bir örnekte denetlenmiş olmalı.
 */
@Import(OpenApiRequiredFieldsTest.IsbnLookupStubConfig.class)
class OpenApiRequiredFieldsTest extends ApiTestSupport {

	private static final String ADMIN = TestJwt.admin(SUBJECT);

	private static final String SCHEMA_PREFIX = "#/components/schemas/";

	private static final String VALID_ISBN13 = "9780306406157";

	@Autowired
	private JsonMapper jsonMapper;

	private JsonNode docs;

	private final Set<String> checkedSchemas = new TreeSet<>();

	private final List<String> violations = new ArrayList<>();

	@Test
	void everyRequiredFieldIsPresentAndNonNullInRealResponses() throws Exception {
		docs = jsonMapper.readTree(mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse()
			.getContentAsString(StandardCharsets.UTF_8));

		String publisherId = check("post", "/api/admin/publishers", 201,
				admin(post("/api/admin/publishers")).content("{\"name\":\"Deniz Yayınları\"}")).get("id").asString();
		String authorId = check("post", "/api/admin/authors", 201,
				admin(post("/api/admin/authors")).content("{\"name\":\"Ayşe Kaya\"}")).get("id").asString();
		String rootId = check("post", "/api/admin/categories", 201,
				admin(post("/api/admin/categories")).content("{\"name\":\"Edebiyat\"}")).get("id").asString();
		String childId = check("post", "/api/admin/categories", 201, admin(post("/api/admin/categories"))
			.content("{\"name\":\"Roman\",\"parentId\":\"%s\"}".formatted(rootId))).get("id").asString();
		// Opsiyonel alanlar (isbn, açıklama, kapak, sayfa sayısı) bilinçli olarak boş bırakılır.
		String bookId = check("post", "/api/admin/books", 201, admin(post("/api/admin/books")).content("""
				{"title":"Deneme","publisherName":"Deniz Yayınları","priceAmount":129.90,"initialStock":5,
				"authorNames":["Ayşe Kaya"],"categoryIds":["%s"]}""".formatted(childId)))
			.get("id").asString();
		check("post", "/api/admin/books/{id}/publish", 200, admin(post("/api/admin/books/{id}/publish", bookId)));
		check("get", "/api/admin/books/isbn-lookup", 200,
				admin(get("/api/admin/books/isbn-lookup").param("isbn", VALID_ISBN13)));

		check("get", "/api/books", 200, get("/api/books"));
		check("get", "/api/books/{id}", 200, get("/api/books/{id}", bookId));
		check("get", "/api/books/lookup", 200, get("/api/books/lookup").param("ids", bookId));
		check("get", "/api/categories", 200, get("/api/categories"));
		check("get", "/api/admin/books", 200, admin(get("/api/admin/books")));
		check("get", "/api/admin/books/{id}", 200, admin(get("/api/admin/books/{id}", bookId)));
		check("get", "/api/admin/publishers", 200, admin(get("/api/admin/publishers")));
		check("get", "/api/admin/publishers/{id}", 200, admin(get("/api/admin/publishers/{id}", publisherId)));
		check("get", "/api/admin/authors", 200, admin(get("/api/admin/authors")));
		check("get", "/api/admin/authors/{id}", 200, admin(get("/api/admin/authors/{id}", authorId)));
		check("get", "/api/admin/categories", 200, admin(get("/api/admin/categories")));
		check("get", "/api/admin/categories/{id}", 200, admin(get("/api/admin/categories/{id}", childId)));

		UUID orderId = UUID.randomUUID();
		check("post", "/internal/stock/reservations", 201, post("/internal/stock/reservations")
			.with(InternalTestKeys.orderServiceKey())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"orderId\":\"%s\",\"items\":[{\"bookId\":\"%s\",\"quantity\":2}]}".formatted(orderId, bookId)));
		check("get", "/internal/stock/reservations/{orderId}", 200,
				get("/internal/stock/reservations/{orderId}", orderId).with(InternalTestKeys.orderServiceKey()));

		assertThat(violations).as("required alan eksik/null").isEmpty();
		assertThat(checkedSchemas).as("örnek yanıtla denetlenen şemalar").containsAll(successResponseSchemas());
	}

	private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
		return request.with(bearer(ADMIN)).contentType(MediaType.APPLICATION_JSON);
	}

	/** İsteği atar, durumu doğrular ve yanıtı dokümandaki şemaya karşı denetler. */
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
		validate(response, schema, method.toUpperCase() + " " + path);
		return response;
	}

	private void validate(JsonNode node, JsonNode schema, String location) {
		if (schema.has("$ref")) {
			String name = schema.get("$ref").asString().substring(SCHEMA_PREFIX.length());
			checkedSchemas.add(name);
			validate(node, docs.path("components").path("schemas").path(name), location + " <" + name + ">");
			return;
		}
		if (schema.has("items")) {
			for (int i = 0; i < node.size(); i++) {
				validate(node.get(i), schema.get("items"), location + "[" + i + "]");
			}
			return;
		}
		for (JsonNode required : schema.path("required")) {
			String field = required.asString();
			if (!node.has(field) || node.get(field).isNull()) {
				violations.add(location + "." + field);
			}
		}
		for (Map.Entry<String, JsonNode> property : schema.path("properties").properties()) {
			JsonNode value = node.get(property.getKey());
			if (value != null && !value.isNull()) {
				validate(value, property.getValue(), location + "." + property.getKey());
			}
		}
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
			String name = schema.get("$ref").asString().substring(SCHEMA_PREFIX.length());
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

	@TestConfiguration
	static class IsbnLookupStubConfig {

		@Bean
		@Primary
		BookMetadataClient bookMetadataClient() {
			return isbn -> Optional.of(new IsbnMetadataResponse(isbn, "Stub Title", null, null, null,
					List.of("Stub Author"), List.of("Stub Publisher"), List.of("Fiction")));
		}

	}

}
