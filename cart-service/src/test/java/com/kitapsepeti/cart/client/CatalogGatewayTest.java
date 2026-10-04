package com.kitapsepeti.cart.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.exception.BookNotAvailableException;
import com.kitapsepeti.cart.exception.CatalogUnavailableException;
import com.kitapsepeti.cart.support.CatalogStub.Response;
import com.kitapsepeti.cart.support.TestJwt;
import feign.Logger;
import feign.Retryer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** {@link CatalogGateway} gerçek Feign istemcisiyle, Catalog taklidine ({@code CATALOG}) karşı. */
@ExtendWith(OutputCaptureExtension.class)
class CatalogGatewayTest extends ApiTestSupport {

	private static final String BOOK_PATH = "/api/books/";

	private static final String LOOKUP_PATH = "/api/books/lookup";

	@Autowired
	private CatalogGateway gateway;

	@Autowired
	private CatalogClient client;

	@Autowired
	private FeignClientProperties feignClientProperties;

	@Autowired
	private FeignClientFactory feignClientFactory;

	// --- requireAvailableBook ---

	@Test
	void mapsBookFieldsKeepsPriceScaleAndIgnoresUnknownFields() {
		UUID id = UUID.randomUUID();
		CATALOG.respond(Response.json(200, detailJson(id, "149.90", true, null)));

		CatalogBook book = gateway.requireAvailableBook(id);

		assertThat(book.id()).isEqualTo(id);
		assertThat(book.title()).isEqualTo("Kürk Mantolu Madonna");
		assertThat(book.priceAmount()).isEqualTo(new BigDecimal("149.90"));
		assertThat(book.priceAmount().scale()).isEqualTo(2);
		assertThat(book.currency()).isEqualTo("TRY");
		assertThat(book.coverUrl()).isNull();
		assertThat(book.inStock()).isTrue();
		assertThat(CATALOG.requests()).singleElement().satisfies(request -> {
			assertThat(request.method()).isEqualTo("GET");
			assertThat(request.path()).isEqualTo(BOOK_PATH + id);
			assertThat(request.query()).isNull();
		});
	}

	@Test
	void mapsCoverUrlWhenPresent() {
		UUID id = UUID.randomUUID();
		CATALOG.respond(Response.json(200, detailJson(id, "20.00", true, "https://cdn.example.com/k.jpg")));

		assertThat(gateway.requireAvailableBook(id).coverUrl()).isEqualTo("https://cdn.example.com/k.jpg");
	}

	@Test
	void notFoundMeansBookNotAvailable() {
		CATALOG.respond(Response.problem(404));

		assertThatThrownBy(() -> gateway.requireAvailableBook(UUID.randomUUID()))
			.isInstanceOf(BookNotAvailableException.class);
	}

	@Test
	void outOfStockMeansBookNotAvailable() {
		UUID id = UUID.randomUUID();
		CATALOG.respond(Response.json(200, detailJson(id, "149.90", false, null)));

		assertThatThrownBy(() -> gateway.requireAvailableBook(id)).isInstanceOf(BookNotAvailableException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = { 500, 502, 503, 504 })
	void serverErrorsMeanCatalogUnavailableAndAreNotRetried(int status) {
		CATALOG.respond(Response.problem(status));

		assertThatThrownBy(() -> gateway.requireAvailableBook(UUID.randomUUID()))
			.isInstanceOf(CatalogUnavailableException.class);
		assertThat(CATALOG.requests()).hasSize(1);
	}

	/** Feign Retry-After'lı 503'ü RetryableException yapar; yeniden deneyen bir Retryer olsaydı ikinci istek giderdi. */
	@Test
	void serviceUnavailableWithRetryAfterIsNotRetried() {
		CATALOG.respond(Response.problem(503).withHeader(HttpHeaders.RETRY_AFTER, "1"));

		assertThatThrownBy(() -> gateway.requireAvailableBook(UUID.randomUUID()))
			.isInstanceOf(CatalogUnavailableException.class);
		assertThat(CATALOG.requests()).hasSize(1);
	}

	@Test
	void slowResponseTimesOutAfterReadTimeoutWithoutRetry() {
		UUID id = UUID.randomUUID();
		CATALOG.respond(Response.json(200, detailJson(id, "149.90", true, null)).delayed(Duration.ofMillis(3500)));

		long start = System.nanoTime();
		assertThatThrownBy(() -> gateway.requireAvailableBook(id))
			.isInstanceOf(CatalogUnavailableException.class)
			.satisfies(ex -> assertThat(NestedExceptionUtils.getRootCause(ex)).isInstanceOf(SocketTimeoutException.class));
		Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

		assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(1900)).isLessThan(Duration.ofSeconds(4));
		assertThat(CATALOG.requests()).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "truncated", "missingPrice", "nullTitle", "missingInStock", "html", "emptyBody", "otherId" })
	void unreadableResponseMeansCatalogUnavailable(String kind) {
		UUID id = UUID.randomUUID();
		String valid = detailJson(id, "149.90", true, null);
		CATALOG.respond(switch (kind) {
			case "truncated" -> Response.json(200, valid.substring(0, valid.length() / 2));
			case "missingPrice" -> Response.json(200, valid.replace("\"priceAmount\":149.90,", ""));
			case "nullTitle" -> Response.json(200, valid.replace("\"title\":\"Kürk Mantolu Madonna\"", "\"title\":null"));
			case "missingInStock" -> Response.json(200, valid.replace("\"inStock\":true,", ""));
			case "html" -> Response.raw(200, "text/html", "<html><body>bakım</body></html>");
			case "emptyBody" -> Response.json(200, "");
			case "otherId" -> Response.json(200, detailJson(UUID.randomUUID(), "149.90", true, null));
			default -> throw new IllegalArgumentException(kind);
		});

		assertThatThrownBy(() -> gateway.requireAvailableBook(id)).isInstanceOf(CatalogUnavailableException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = { 400, 401, 403, 409, 422 })
	void otherClientErrorsAreOurBug(int status) {
		UUID id = UUID.randomUUID();
		CATALOG.respond(Response.problem(status));

		assertThatThrownBy(() -> gateway.requireAvailableBook(id))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Catalog rejected GET_BOOK request with HTTP " + status)
			.hasNoCause();
	}

	// --- lookup ---

	@Test
	void lookupReturnsFoundBooksKeyedByIdUsingRepeatedQueryParameters() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID third = UUID.randomUUID();
		UUID missing = UUID.randomUUID();
		CATALOG.respond(Response.json(200, lookupJson(summaryJson(third, "30.00", true), summaryJson(first, "10.50", false),
				summaryJson(second, "20.00", true))));

		Map<UUID, CatalogBook> books = gateway.lookup(List.of(first, missing, second, third));

		assertThat(books).containsOnlyKeys(first, second, third).doesNotContainKey(missing);
		assertThat(books.get(first).priceAmount()).isEqualTo(new BigDecimal("10.50"));
		assertThat(books.get(first).inStock()).isFalse();
		assertThat(books.get(third).coverUrl()).isNull();
		assertThat(CATALOG.requests()).singleElement().satisfies(request -> {
			assertThat(request.path()).isEqualTo(LOOKUP_PATH);
			assertThat(request.query()).isEqualTo(repeated(first, missing, second, third));
		});
	}

	@Test
	void lookupSendsDuplicateIdsOnceAndIgnoresUnrequestedBooks() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		CATALOG.respond(Response.json(200,
				lookupJson(summaryJson(first, "10.00", true), summaryJson(UUID.randomUUID(), "99.00", true))));

		Map<UUID, CatalogBook> books = gateway.lookup(List.of(first, second, first));

		assertThat(books).containsOnlyKeys(first);
		assertThat(CATALOG.requests()).singleElement()
			.satisfies(request -> assertThat(request.query()).isEqualTo(repeated(first, second)));
	}

	@Test
	void lookupOfEmptyCollectionDoesNotCallCatalog() {
		assertThat(gateway.lookup(List.of())).isEmpty();
		assertThat(CATALOG.requests()).isEmpty();
	}

	@Test
	void lookupAcceptsFiftyIdsAndRejectsFiftyOneWithoutCallingCatalog() {
		CATALOG.respond(Response.json(200, lookupJson()));
		List<UUID> fifty = randomIds(CatalogGateway.MAX_LOOKUP_IDS);

		assertThat(gateway.lookup(fifty)).isEmpty();
		assertThat(CATALOG.requests()).singleElement()
			.satisfies(request -> assertThat(request.query().split("&")).hasSize(CatalogGateway.MAX_LOOKUP_IDS));

		List<UUID> fiftyOne = new ArrayList<>(fifty);
		fiftyOne.add(UUID.randomUUID());
		assertThatThrownBy(() -> gateway.lookup(fiftyOne)).isInstanceOf(IllegalArgumentException.class);
		assertThat(CATALOG.requests()).hasSize(1);
	}

	@Test
	void lookupServerErrorAndTimeoutMeanCatalogUnavailable() {
		CATALOG.respond(Response.problem(500));
		assertThatThrownBy(() -> gateway.lookup(List.of(UUID.randomUUID())))
			.isInstanceOf(CatalogUnavailableException.class);

		CATALOG.respond(Response.json(200, lookupJson()).delayed(Duration.ofMillis(3500)));
		assertThatThrownBy(() -> gateway.lookup(List.of(UUID.randomUUID())))
			.isInstanceOf(CatalogUnavailableException.class);
		assertThat(CATALOG.requests()).hasSize(2);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"items\":null}", "{\"items\":[null]}", "{\"items\":[{\"id\":\"x\"}]}", "[" })
	void lookupUnreadableResponseMeansCatalogUnavailable(String body) {
		CATALOG.respond(Response.json(200, body));

		assertThatThrownBy(() -> gateway.lookup(List.of(UUID.randomUUID())))
			.isInstanceOf(CatalogUnavailableException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = { 400, 404 })
	void lookupClientErrorsAreOurBug(int status) {
		CATALOG.respond(Response.problem(status));

		assertThatThrownBy(() -> gateway.lookup(List.of(UUID.randomUUID())))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Catalog rejected LOOKUP request with HTTP " + status);
	}

	// --- header'lar ve loglar (gerçek istek içinden) ---

	@Test
	void userTokenAndIncomingHeadersAreNotForwardedToCatalog() throws Exception {
		UUID id = UUID.randomUUID();
		String token = TestJwt.user(SUBJECT);
		CATALOG.respondWith(request -> request.path().equals(LOOKUP_PATH)
				? Response.json(200, lookupJson(summaryJson(id, "10.00", true)))
				: Response.json(200, detailJson(id, "149.90", true, null)));

		mockMvc.perform(get("/api/cart/_catalog/books/{id}", id).with(bearer(token))
				.header(HttpHeaders.COOKIE, "SESSION=cerez-4471")
				.header("X-Correlation-Id", "korelasyon-4471"))
			.andExpect(status().isOk())
			.andExpect(content().string("Kürk Mantolu Madonna"));
		mockMvc.perform(get("/api/cart/_catalog/lookup").param("ids", id.toString()).with(bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0]").value(id.toString()));

		assertThat(CATALOG.requests()).hasSize(2).allSatisfy(request -> {
			assertThat(request.hasHeader(HttpHeaders.AUTHORIZATION)).isFalse();
			assertThat(request.hasHeader(HttpHeaders.COOKIE)).isFalse();
			assertThat(request.hasHeader("X-Correlation-Id")).isFalse();
			assertThat(request.headers().values().toString()).doesNotContain(token).doesNotContain("4471");
		});
	}

	@Test
	void catalogOutageLogsSingleWarnLineWithoutUrlHostBookIdOrBody(CapturedOutput output) throws Exception {
		UUID id = UUID.randomUUID();
		CATALOG.respond(Response.problem(503));

		String body = mockMvc.perform(get("/api/cart/_catalog/books/{id}", id).with(bearer(TestJwt.user(SUBJECT))))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
			.andReturn().getResponse().getContentAsString();

		assertThat(output.getOut().lines().filter(line -> line.contains("-> CATALOG_UNAVAILABLE")))
			.singleElement()
			.satisfies(line -> assertThat(line).contains(" WARN ")
				.contains("GET /api/cart/_catalog/books/:id -> CATALOG_UNAVAILABLE (cause=ServiceUnavailable)"));
		assertNoCatalogDetails(body, output, id, true);
	}

	@Test
	void catalogTimeoutLogsSingleWarnLineWithCauseClassOnly(CapturedOutput output) throws Exception {
		UUID id = UUID.randomUUID();
		CATALOG.respond(Response.json(200, lookupJson()).delayed(Duration.ofMillis(3500)));

		String body = mockMvc.perform(get("/api/cart/_catalog/lookup").param("ids", id.toString())
				.with(bearer(TestJwt.user(SUBJECT))))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
			.andReturn().getResponse().getContentAsString();

		assertThat(output.getOut().lines().filter(line -> line.contains("-> CATALOG_UNAVAILABLE")))
			.singleElement()
			.satisfies(line -> assertThat(line).contains(" WARN ")
				.endsWith("GET /api/cart/_catalog/lookup -> CATALOG_UNAVAILABLE (cause=SocketTimeoutException)"));
		assertNoCatalogDetails(body, output, id, true);
	}

	/** Bizim hatamız (500): ERROR + stack trace yazılır ama Catalog adresi, kitap id'si ve gövde yine yok. */
	@Test
	void catalogClientErrorLogsNoCatalogDetails(CapturedOutput output) throws Exception {
		UUID id = UUID.randomUUID();
		CATALOG.respond(Response.problem(400));

		mockMvc.perform(get("/api/cart/_catalog/lookup").param("ids", id.toString()).with(bearer(TestJwt.user(SUBJECT))))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

		assertThat(output).contains(" ERROR ").contains("Catalog rejected LOOKUP request with HTTP 400");
		assertNoCatalogDetails("", output, id, false);
	}

	// --- bağlam ---

	@Test
	void feignClientUsesTimeoutsAndLoggerFromYamlAndNeverRetries() {
		assertThat(client).isNotNull();
		var catalog = feignClientProperties.getConfig().get("catalog");
		assertThat(catalog.getConnectTimeout()).isEqualTo(1000);
		assertThat(catalog.getReadTimeout()).isEqualTo(2000);
		assertThat(catalog.getLoggerLevel()).isEqualTo(Logger.Level.NONE);
		assertThat(catalog.getRetryer()).isNull();
		assertThat(catalog.getRequestInterceptors()).isNullOrEmpty();
		assertThat(feignClientFactory.getInstance("catalog", Retryer.class)).isSameAs(Retryer.NEVER_RETRY);
	}

	private static void assertNoCatalogDetails(String body, CapturedOutput output, UUID id, boolean noStackTrace) {
		for (String value : List.of(CATALOG.baseUrl(), ":" + CATALOG.port(), BOOK_PATH, Response.BODY_MARKER)) {
			assertThat(body).doesNotContain(value);
			assertThat(output).doesNotContain(value);
		}
		// Probe yolu desen dışı: UUID güvenlik ağıyla instance'ta ve logda :id olur.
		assertThat(body).doesNotContain(id.toString());
		assertThat(output).doesNotContain(id.toString());
		if (noStackTrace) {
			assertThat(output).doesNotContain("Caused by").doesNotContain("\tat ").doesNotContain("FeignException");
		}
	}

	// --- Catalog yanıtları (bilinmeyen alanlar dahil, sözleşmedeki gibi) ---

	private static String detailJson(UUID id, String price, boolean inStock, String coverUrl) {
		return """
				{"id":"%s","title":"Kürk Mantolu Madonna","isbn":"9789753638029","description":"Roman",
				"pageCount":160,"priceAmount":%s,"currency":"TRY","coverUrl":%s,"inStock":%s,
				"publishedAt":"2026-01-01T00:00:00Z","publisher":{"id":"%s","name":"YKY","slug":"yky"},
				"authors":[{"id":"%s","name":"Sabahattin Ali","slug":"sabahattin-ali"}],"categories":[],
				"gelecektekiAlan":{"ic":[1,2,3]}}"""
			.formatted(id, price, (coverUrl == null) ? "null" : "\"" + coverUrl + "\"", inStock, UUID.randomUUID(),
					UUID.randomUUID())
			.replace("\n", "");
	}

	private static String summaryJson(UUID id, String price, boolean inStock) {
		return """
				{"id":"%s","title":"Kitap","coverUrl":null,"priceAmount":%s,"currency":"TRY","inStock":%s,
				"publisher":{"id":"%s","name":"YKY","slug":"yky"},"authors":[],"puan":4.5}"""
			.formatted(id, price, inStock, UUID.randomUUID())
			.replace("\n", "");
	}

	private static String lookupJson(String... items) {
		return "{\"items\":[" + String.join(",", items) + "],\"toplam\":" + items.length + "}";
	}

	private static String repeated(UUID... ids) {
		return Stream.of(ids).map(id -> "ids=" + id).collect(Collectors.joining("&"));
	}

	private static List<UUID> randomIds(int count) {
		return Stream.generate(UUID::randomUUID).limit(count).toList();
	}

}
