package com.kitapsepeti.cart.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.exception.CartErrorCode;
import com.kitapsepeti.cart.support.FakeCatalog;
import com.kitapsepeti.cart.support.InternalTestKeys;
import com.kitapsepeti.cart.support.TestJwt;
import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class OpenApiDocsTest extends ApiTestSupport {

	private static final String PROBLEM_JSON = "application/problem+json";

	private static final String PROBLEM_REF = "#/components/schemas/Problem";

	private static final String LIMIT_PROBLEM_REF = "#/components/schemas/CartLimitProblem";

	private static final String GET_CART = "GET /api/cart";

	private static final String ADD_ITEM = "POST /api/cart/items";

	private static final String CHANGE_QUANTITY = "PATCH /api/cart/items/{bookId}";

	private static final String REMOVE_ITEM = "DELETE /api/cart/items/{bookId}";

	private static final String CLEAR = "DELETE /api/cart/items";

	private static final String SNAPSHOT = "POST /internal/cart/snapshot";

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

	/** Internal zinciri yalnızca /internal/**'ı eşler: doküman uçları anahtar istemez, anahtar başlığı da etkilemez. */
	@Test
	void internalChainDoesNotAffectApiDocs() throws Exception {
		for (String key : List.of(InternalTestKeys.ORDER_SERVICE_KEY, InternalTestKeys.randomKey())) {
			mockMvc.perform(get("/v3/api-docs").header(InternalApiKeyAuthenticationFilter.HEADER, key))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").value(startsWith("3.")));
			mockMvc.perform(get("/swagger-ui/index.html").header(InternalApiKeyAuthenticationFilter.HEADER, key))
				.andExpect(status().isOk());
		}
	}

	@Test
	void documentsExactlyTheRealOperations() throws Exception {
		assertThat(operations(docs()).keySet())
			.containsExactlyInAnyOrder(GET_CART, ADD_ITEM, CHANGE_QUANTITY, REMOVE_ITEM, CLEAR, SNAPSHOT);
	}

	/** Deneme uçları bağlamda gerçekten var (security/exception/client paketleri) ama dokümana girmez. */
	@Test
	void testOnlyControllersActuatorAndTheirSchemasAreNotDocumented() throws Exception {
		String user = TestJwt.user(SUBJECT);
		mockMvc.perform(get("/api/cart/_whoami").with(bearer(user))).andExpect(status().isOk());
		mockMvc.perform(get("/api/other/ping").with(bearer(user))).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

		DocumentContext docs = docs();
		Map<String, Object> paths = docs.read("$.paths");
		assertThat(paths.keySet()).noneMatch(path -> path.contains("_") || path.startsWith("/actuator")
				|| path.startsWith("/api/other"))
			.containsExactlyInAnyOrder("/api/cart", "/api/cart/items", "/api/cart/items/{bookId}",
					"/internal/cart/snapshot");
		Map<String, Object> schemas = docs.read("$.components.schemas");
		assertThat(schemas.keySet()).containsExactlyInAnyOrder("AddCartItemRequest", "UpdateCartItemRequest",
				"CartResponse", "CartLineResponse", "CatalogStatus", "CartSnapshotRequest", "CartSnapshotResponse",
				"CartSnapshotItem", "Problem", "CartLimitProblem", "FieldError");
	}

	@Test
	void tagsAreSortedAndEveryOperationHasOne() throws Exception {
		DocumentContext docs = docs();
		assertThat(docs.<List<String>>read("$.tags[*].name")).containsExactly("Cart", "Internal");
		operations(docs).forEach((operation, spec) -> assertThat(spec.get("tags")).as(operation)
			.isEqualTo(List.of(operation.equals(SNAPSHOT) ? "Internal" : "Cart")));
	}

	/** Herkese açık operasyon yok: global security tanımsız, her operasyon yol önekine göre tek şema taşır. */
	@Test
	void everyOperationHasExactlyTheSecuritySchemeOfItsPathPrefix() throws Exception {
		DocumentContext docs = docs();
		assertThat(docs.<Map<String, Object>>read("$")).doesNotContainKey("security");
		assertThat(docs.<Map<String, Object>>read("$.components.securitySchemes")).containsOnlyKeys("bearerAuth",
				"internalApiKey");
		assertThat(docs.<Map<String, Object>>read("$.components.securitySchemes.bearerAuth"))
			.containsEntry("type", "http").containsEntry("scheme", "bearer").containsEntry("bearerFormat", "JWT");
		assertThat(docs.<Map<String, Object>>read("$.components.securitySchemes.internalApiKey"))
			.containsEntry("type", "apiKey").containsEntry("in", "header").containsEntry("name", "X-Internal-Api-Key");

		operations(docs).forEach((operation, spec) -> {
			String scheme = path(operation).startsWith("/internal/") ? "internalApiKey" : "bearerAuth";
			assertThat(spec.get("security")).as(operation).isEqualTo(List.of(Map.of(scheme, List.of())));
		});
		for (String operation : List.of(GET_CART, ADD_ITEM, CHANGE_QUANTITY, REMOVE_ITEM, CLEAR)) {
			assertThat(header401Example(docs, operation)).as(operation).isEqualTo("Bearer");
		}
		assertThat(header401Example(docs, SNAPSHOT)).isEqualTo("ApiKey realm=\"internal\"");
	}

	/** Her operasyonun yanıt kodları tam olarak gerçek davranış; uydurma kod yok. */
	@Test
	void everyOperationDocumentsExactlyItsRealResponseCodes() throws Exception {
		Map<String, Map<String, Object>> operations = operations(docs());

		assertThat(responses(operations.get(GET_CART))).containsOnlyKeys("200", "401", "500", "503");
		assertThat(responses(operations.get(ADD_ITEM))).containsOnlyKeys("200", "400", "401", "409", "500", "503");
		assertThat(responses(operations.get(CHANGE_QUANTITY)))
			.containsOnlyKeys("200", "400", "401", "404", "409", "500", "503");
		assertThat(responses(operations.get(REMOVE_ITEM))).containsOnlyKeys("200", "400", "401", "500", "503");
		assertThat(responses(operations.get(CLEAR))).containsOnlyKeys("200", "401", "500", "503");
		assertThat(responses(operations.get(SNAPSHOT))).containsOnlyKeys("200", "400", "401", "500");
	}

	@Test
	void conflictResponsesDocumentTheirCodesAndTheLimitExtension() throws Exception {
		DocumentContext docs = docs();

		assertThat(problemRef(docs, ADD_ITEM, "409")).isEqualTo(LIMIT_PROBLEM_REF);
		assertThat(problemRef(docs, CHANGE_QUANTITY, "409")).isEqualTo(LIMIT_PROBLEM_REF);
		assertThat(description(docs, ADD_ITEM, "409")).contains("`BOOK_NOT_AVAILABLE`", "`CART_LINE_LIMIT_EXCEEDED`",
				"`CART_QUANTITY_LIMIT_EXCEEDED`");
		assertThat(description(docs, CHANGE_QUANTITY, "409")).contains("`CART_QUANTITY_LIMIT_EXCEEDED`")
			.doesNotContain("BOOK_NOT_AVAILABLE", "CART_LINE_LIMIT_EXCEEDED");
		assertThat(description(docs, ADD_ITEM, "503")).contains("`CATALOG_UNAVAILABLE`", "`AUTHENTICATION_UNAVAILABLE`");
		assertThat(description(docs, CHANGE_QUANTITY, "503")).contains("`AUTHENTICATION_UNAVAILABLE`")
			.doesNotContain("CATALOG_UNAVAILABLE");
		assertThat(description(docs, CHANGE_QUANTITY, "404")).contains("`RESOURCE_NOT_FOUND`");

		assertThat(docs.<String>read("$.components.schemas.CartLimitProblem.allOf[0]['$ref']")).isEqualTo(PROBLEM_REF);
		assertThat(docs.<Map<String, Object>>read("$.components.schemas.CartLimitProblem.allOf[1].properties.limit"))
			.containsEntry("type", "integer");
	}

	/** Doküman sırası API_CODES'tan; liste servisin tüm kodlarını ve tüm ortak kodları kapsar. */
	@Test
	void problemCodeEnumMatchesApiCodesInOrder() throws Exception {
		List<String> documented = docs().read("$.components.schemas.Problem.properties.code.enum");

		assertThat(documented).containsExactlyElementsOf(CartErrorCode.API_CODES.stream().map(ErrorCode::name).toList());
		Set<ErrorCode> returnable = new HashSet<>(EnumSet.allOf(CartErrorCode.class));
		returnable.addAll(EnumSet.allOf(CommonErrorCode.class));
		assertThat(CartErrorCode.API_CODES).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(returnable);
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
	void validationBoundsNullabilityAndEnumsAreDocumented() throws Exception {
		DocumentContext docs = docs();

		for (String schema : List.of("AddCartItemRequest", "UpdateCartItemRequest")) {
			assertThat(docs.<Map<String, Object>>read("$.components.schemas.%s.properties.quantity".formatted(schema)))
				.as(schema).containsEntry("minimum", 1).containsEntry("maximum", 99);
		}
		assertThat(docs.<Integer>read("$.components.schemas.AddCartItemRequest.properties.quantity.default")).isEqualTo(1);
		assertThat(docs.<List<String>>read("$.components.schemas.AddCartItemRequest.required")).containsExactly("bookId");
		assertThat(docs.<List<String>>read("$.components.schemas.UpdateCartItemRequest.required")).containsExactly("quantity");
		assertThat(docs.<Map<String, Object>>read("$.components.schemas.CartSnapshotRequest.properties.userId"))
			.containsEntry("type", "string").containsEntry("format", "uuid");
		assertThat(docs.<List<String>>read("$.components.schemas.CartSnapshotRequest.required")).containsExactly("userId");
		assertThat(docs.<List<String>>read("$.components.schemas.CatalogStatus.enum"))
			.containsExactly("VERIFIED", "UNAVAILABLE");

		assertThat(nullableProperties(docs)).isEqualTo(Map.of(
				"CartResponse", Set.of("currency", "subtotal"),
				"CartLineResponse", Set.of("available", "coverUrl", "currentUnitPrice"),
				"CartSnapshotResponse", Set.of("cartId", "updatedAt")));
		for (String money : List.of("CartResponse.subtotal", "CartLineResponse.snapshotUnitPrice",
				"CartLineResponse.currentUnitPrice", "CartLineResponse.lineTotal", "CartSnapshotItem.unitPriceSnapshot")) {
			String[] parts = money.split("\\.");
			Map<String, Object> property = docs.read("$.components.schemas.%s.properties.%s".formatted(parts[0], parts[1]));
			assertThat(property).as(money).doesNotContainKey("format");
			assertThat(String.valueOf(property.get("type"))).as(money).contains("number");
			assertThat((String) property.get("description")).as(money).contains("2 ondalık basamak");
		}
	}

	/** Örnek isteklerin gerçek hata yanıtı dokümandaki kod, açıklama, şema ve başlıkla uyuşur. */
	@Test
	void documentedErrorsMatchRealResponses() throws Exception {
		FakeCatalog catalog = new FakeCatalog();
		CATALOG.respondWith(catalog);
		String user = TestJwt.user(SUBJECT);
		DocumentContext docs = docs();

		FakeCatalog.Book book = catalog.publish("Deneme", "50.00");
		MockHttpServletResponse limit = assertDocumentedError(docs, ADD_ITEM, 409, post("/api/cart/items")
			.with(bearer(user)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"bookId\":\"%s\",\"quantity\":11}".formatted(book.id())));
		assertThat(JsonPath.<String>read(limit.getContentAsString(), "$.code"))
			.isEqualTo(CartErrorCode.CART_QUANTITY_LIMIT_EXCEEDED.name());
		assertThat(JsonPath.<Integer>read(limit.getContentAsString(), "$.limit")).isEqualTo(10);

		assertDocumentedError(docs, CHANGE_QUANTITY, 404, patch("/api/cart/items/{id}", UUID.randomUUID())
			.with(bearer(user)).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"));
		assertDocumentedError(docs, REMOVE_ITEM, 400, delete("/api/cart/items/{id}", "not-a-uuid").with(bearer(user)));
		assertDocumentedError(docs, ADD_ITEM, 400, post("/api/cart/items").with(bearer(user))
			.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"));

		MockHttpServletResponse unauthorized = assertDocumentedError(docs, GET_CART, 401, get("/api/cart"));
		assertThat(unauthorized.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(header401Example(docs, GET_CART));

		MockHttpServletResponse internal = assertDocumentedError(docs, SNAPSHOT, 401, post("/internal/cart/snapshot")
			.contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"%s\"}".formatted(UUID.randomUUID())));
		assertThat(internal.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(header401Example(docs, SNAPSHOT));
	}

	/**
	 * Durum kodu operasyonda belgeli, gövde problem+json, {@code code} o yanıtın açıklamasında ve enum'da geçer,
	 * Problem'in required alanları var ve gövdede dokümanın yanıt şemasında olmayan alan yok.
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

		Set<String> documented = new HashSet<>(docs.<Map<String, Object>>read("$.components.schemas.Problem.properties").keySet());
		if (LIMIT_PROBLEM_REF.equals(problemRef(docs, operation, String.valueOf(status)))) {
			documented.addAll(docs.<Map<String, Object>>read("$.components.schemas.CartLimitProblem.allOf[1].properties").keySet());
		}
		assertThat(documented).as(operation).containsAll(problem.keySet());
		return response;
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

	private static String problemRef(DocumentContext docs, String operation, String code) {
		return docs.read(responsePath(operation, code) + ".content['%s'].schema['$ref']".formatted(PROBLEM_JSON));
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
