package com.kitapsepeti.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.exception.CatalogErrorCode;
import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

class OpenApiDocsTest extends ApiTestSupport {

	private static final String PROBLEM_JSON = "application/problem+json";

	private static final String PROBLEM_REF = "#/components/schemas/Problem";

	private static final String STOCK_PROBLEM_REF = "#/components/schemas/StockUnavailableProblem";

	private static final List<String> STOCK_FIELDS = List.of("stockQuantity", "reservedQuantity", "availableQuantity");

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

	@Test
	void documentsExactlyTheRealOperationsAndNoTestEndpoints() throws Exception {
		assertThat(operations(docs()).keySet()).containsExactlyInAnyOrder(
				"GET /api/books", "GET /api/books/lookup", "GET /api/books/{id}", "GET /api/categories",
				"GET /api/admin/books", "POST /api/admin/books", "GET /api/admin/books/isbn-lookup",
				"GET /api/admin/books/{id}",
				"PATCH /api/admin/books/{id}", "DELETE /api/admin/books/{id}", "POST /api/admin/books/{id}/publish",
				"POST /api/admin/books/{id}/archive", "POST /api/admin/books/{id}/stock-adjustments",
				"GET /api/admin/publishers", "POST /api/admin/publishers", "GET /api/admin/publishers/{id}",
				"PATCH /api/admin/publishers/{id}", "DELETE /api/admin/publishers/{id}",
				"GET /api/admin/authors", "POST /api/admin/authors", "GET /api/admin/authors/{id}",
				"PATCH /api/admin/authors/{id}", "DELETE /api/admin/authors/{id}",
				"GET /api/admin/categories", "POST /api/admin/categories", "GET /api/admin/categories/{id}",
				"PATCH /api/admin/categories/{id}", "PUT /api/admin/categories/{id}/parent",
				"DELETE /api/admin/categories/{id}",
				"POST /internal/stock/reservations", "GET /internal/stock/reservations/{orderId}",
				"POST /internal/stock/reservations/{orderId}/commit",
				"POST /internal/stock/reservations/{orderId}/release");
	}

	@Test
	void publicOperationsHaveEmptySecurityAndThereIsNoGlobalRequirement() throws Exception {
		DocumentContext docs = docs();

		assertThat(docs.<Map<String, Object>>read("$")).doesNotContainKey("security");
		for (String path : List.of("/api/books", "/api/books/lookup", "/api/books/{id}", "/api/categories")) {
			assertThat(docs.<List<Object>>read("$.paths['%s'].get.security".formatted(path))).as(path).isEmpty();
			assertThat(docs.<Map<String, Object>>read("$.paths['%s'].get.responses".formatted(path))).as(path)
				.doesNotContainKeys("401", "403", "503");
		}
	}

	@Test
	void everyAdminOperationRequiresBearerAuth() throws Exception {
		DocumentContext docs = docs();
		assertThat(docs.<Map<String, Object>>read("$.components.securitySchemes.bearerAuth"))
			.containsEntry("type", "http").containsEntry("scheme", "bearer").containsEntry("bearerFormat", "JWT");

		Map<String, Map<String, Object>> admin = operationsUnder(docs, "/api/admin/");
		assertThat(admin).hasSize(25);
		admin.forEach((operation, spec) -> {
			assertThat(spec.get("security")).as(operation).isEqualTo(List.of(Map.of("bearerAuth", List.of())));
			assertThat(responses(spec)).as(operation).containsKeys("401", "403", "503");
		});
	}

	@Test
	void everyInternalOperationRequiresInternalApiKey() throws Exception {
		DocumentContext docs = docs();
		assertThat(docs.<Map<String, Object>>read("$.components.securitySchemes.internalApiKey"))
			.containsEntry("type", "apiKey").containsEntry("in", "header").containsEntry("name", "X-Internal-Api-Key");

		Map<String, Map<String, Object>> internal = operationsUnder(docs, "/internal/");
		assertThat(internal).hasSize(4);
		internal.forEach((operation, spec) -> {
			assertThat(spec.get("security")).as(operation).isEqualTo(List.of(Map.of("internalApiKey", List.of())));
			assertThat(responses(spec)).as(operation).containsKey("401").doesNotContainKeys("403", "503");
		});
		assertThat(docs.<String>read("$.paths['/internal/stock/reservations'].post.responses['401']"
				+ ".headers['WWW-Authenticate'].schema.example")).isEqualTo("ApiKey realm=\"internal\"");
	}

	@Test
	void reservationConflictUsesStockProblemWithBookIds() throws Exception {
		DocumentContext docs = docs();

		assertThat(docs.<String>read("$.paths['/internal/stock/reservations'].post.responses['409'].content['%s']"
				.formatted(PROBLEM_JSON) + ".schema['$ref']")).isEqualTo(STOCK_PROBLEM_REF);
		assertThat(docs.<String>read("$.components.schemas.StockUnavailableProblem.allOf[0]['$ref']"))
			.isEqualTo(PROBLEM_REF);
		Map<String, Object> bookIds = docs.read("$.components.schemas.StockUnavailableProblem.allOf[1].properties.bookIds");
		assertThat(bookIds).containsEntry("type", "array")
			.containsEntry("items", Map.of("type", "string", "format", "uuid"));
		// Diğer 409'lar bookIds taşımaz.
		assertThat(problemRef(docs, "/internal/stock/reservations/{orderId}/commit", "post", "409")).isEqualTo(PROBLEM_REF);
	}

	/** Doküman sırası API_CODES'tan; liste servisin tüm kodlarını ve tüm ortak kodları kapsar. */
	@Test
	void problemCodeEnumMatchesErrorCodeNames() throws Exception {
		List<String> documented = docs().read("$.components.schemas.Problem.properties.code.enum");

		assertThat(documented).containsExactlyElementsOf(CatalogErrorCode.API_CODES.stream().map(ErrorCode::name).toList());
		Set<ErrorCode> returnable = new HashSet<>(EnumSet.allOf(CatalogErrorCode.class));
		returnable.addAll(EnumSet.allOf(CommonErrorCode.class));
		assertThat(CatalogErrorCode.API_CODES).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(returnable);
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
	void publicBookSchemasExposeOnlyInStockWhileAdminSchemasExposeCounts() throws Exception {
		DocumentContext docs = docs();

		for (String schema : List.of("BookSummaryResponse", "BookDetailResponse")) {
			Map<String, Object> properties = docs.read("$.components.schemas.%s.properties".formatted(schema));
			assertThat(properties).as(schema).containsKey("inStock").doesNotContainKeys(STOCK_FIELDS.toArray(String[]::new));
		}
		for (String schema : List.of("AdminBookResponse", "AdminBookSummaryResponse")) {
			Map<String, Object> properties = docs.read("$.components.schemas.%s.properties".formatted(schema));
			assertThat(properties).as(schema).containsKeys(STOCK_FIELDS.toArray(String[]::new));
		}
	}

	@Test
	void validationConstraintsAndEnumsAreDocumented() throws Exception {
		DocumentContext docs = docs();

		assertThat(docs.<List<String>>read("$.paths['/api/books'].get.parameters[?(@.name == 'sort')].schema.enum[*]"))
			.containsExactly("newest", "price_asc", "price_desc", "title_asc");
		assertThat(docs.<List<String>>read("$.paths['/api/admin/books'].get.parameters[?(@.name == 'status')].schema.enum[*]"))
			.containsExactly("draft", "published", "archived");
		assertThat(docs.<List<String>>read("$.components.schemas.UpdateBookRequest.required")).contains("version");
		assertThat(docs.<Map<String, Object>>read("$.components.schemas.ReserveStockRequest.properties.items"))
			.containsEntry("minItems", 1).containsEntry("maxItems", 50);
		assertThat(docs.<Map<String, Object>>read("$.components.schemas.ReserveStockItem.properties.quantity"))
			.containsEntry("minimum", 1).containsEntry("maximum", 100);
		assertThat(docs.<Map<String, Object>>read("$.components.schemas.CreateBookRequest.properties.initialStock"))
			.containsEntry("minimum", 0).containsEntry("maximum", 1_000_000);
		assertThat(docs.<Map<String, Object>>read("$.components.schemas.StockAdjustmentRequest.properties.delta"))
			.containsEntry("minimum", -100_000).containsEntry("maximum", 100_000);
		assertThat(docs.<Map<String, Object>>read("$.components.schemas.PageResponseBookSummaryResponse.properties"))
			.containsOnlyKeys("items", "page", "size", "totalElements", "totalPages");
		assertThat(docs.<String>read("$.components.schemas.ReservationItem.properties.unitPrice.type"))
			.isEqualTo(docs.<String>read("$.components.schemas.BookSummaryResponse.properties.priceAmount.type"))
			.isEqualTo("number");
	}

	@Test
	void realErrorResponseMatchesDocumentedProblemShape() throws Exception {
		mockMvc.perform(get("/api/books/{id}", UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.title").isString())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.code").value(CommonErrorCode.RESOURCE_NOT_FOUND.name()))
			.andExpect(jsonPath("$.instance").value(startsWith("/api/books/")))
			.andExpect(jsonPath("$.type").doesNotExist());
	}

	private static String problemRef(DocumentContext docs, String path, String method, String code) {
		return docs.read("$.paths['%s'].%s.responses['%s'].content['%s'].schema['$ref']"
			.formatted(path, method, code, PROBLEM_JSON));
	}

	/** "METHOD /yol" → operasyon. */
	private static Map<String, Map<String, Object>> operations(DocumentContext docs) {
		Map<String, Map<String, Map<String, Object>>> paths = docs.read("$.paths");
		Map<String, Map<String, Object>> operations = new TreeMap<>();
		paths.forEach((path, item) -> item.forEach((method, spec) -> operations.put(method.toUpperCase() + " " + path, spec)));
		return operations;
	}

	private static Map<String, Map<String, Object>> operationsUnder(DocumentContext docs, String prefix) {
		Map<String, Map<String, Object>> selected = new TreeMap<>();
		operations(docs).forEach((operation, spec) -> {
			if (operation.substring(operation.indexOf(' ') + 1).startsWith(prefix)) {
				selected.put(operation, spec);
			}
		});
		return selected;
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
