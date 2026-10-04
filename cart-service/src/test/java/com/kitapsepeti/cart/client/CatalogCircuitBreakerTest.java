package com.kitapsepeti.cart.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.support.CatalogStub.Response;
import com.kitapsepeti.cart.support.FakeCatalog;
import com.kitapsepeti.cart.support.FakeCatalog.Book;
import com.kitapsepeti.cart.support.TestJwt;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Catalog çağrılarının circuit breaker'ı ({@code catalog}): son 20 çağrının en az 10'u varken %50 teknik hata açar,
 * açıkken Catalog'a istek gitmez ve yanıtlar devre kapalıyken alınan "Catalog yok" yanıtlarıyla aynıdır.
 */
@ExtendWith(OutputCaptureExtension.class)
class CatalogCircuitBreakerTest extends ApiTestSupport {

	private static final Instant T1 = Instant.parse("2026-03-01T10:15:30.123456Z");

	/** 5xx, 500 ve sözleşmeye uymayan 2xx dönüşümlü: hepsi teknik hata. */
	private static final List<Response> TECHNICAL_FAILURES = List.of(Response.problem(503), Response.problem(500),
			Response.raw(200, "application/json", "{\"bozuk\":"));

	private final FakeCatalog catalog = new FakeCatalog();

	private String token;

	@BeforeEach
	void useFakeCatalog() {
		CATALOG.respondWith(catalog);
		token = TestJwt.user(SUBJECT);
	}

	@Test
	void opensAfterTenTechnicalFailuresAndThenSendsNoRequests(CapturedOutput output) throws Exception {
		Book book = catalog.publish("Devre", "10.00");
		for (int i = 0; i < 10; i++) {
			assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.CLOSED);
			catalog.failWith(TECHNICAL_FAILURES.get(i % TECHNICAL_FAILURES.size()));
			add(book.id()).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
		}
		assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.OPEN);
		assertThat(CATALOG.requests()).hasSize(10);

		catalog.recover();
		for (int i = 0; i < 5; i++) {
			add(book.id()).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
		}
		assertThat(CATALOG.requests()).hasSize(10);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isZero();

		List<String> transitions = output.getOut().lines().filter(line -> line.contains("Circuit breaker ")).toList();
		assertThat(transitions).singleElement()
			.satisfies(line -> assertThat(line).contains(" WARN ").endsWith("Circuit breaker catalog CLOSED -> OPEN"));
		for (String secret : List.of(book.id().toString(), SUBJECT, token, "10.00", CATALOG.baseUrl())) {
			assertThat(output).doesNotContain(secret);
		}
	}

	@Test
	void responsesWhileOpenMatchTheCatalogUnavailableResponses(CapturedOutput output) throws Exception {
		clock.fixAt(T1);
		Book inCart = catalog.publish("Sepette", "149.90");
		Book other = catalog.publish("Yeni", "50.00");
		add(inCart.id()).andExpect(status().isOk());
		catalog.failWith(Response.problem(503));

		MockHttpServletResponse addWhileClosed = add(other.id()).andReturn().getResponse();
		MockHttpServletResponse viewWhileClosed = getCart().andReturn().getResponse();
		assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.CLOSED);
		for (int i = 0; i < 10 && catalogCircuitBreaker.getState() == State.CLOSED; i++) {
			add(other.id()).andExpect(status().isServiceUnavailable());
		}
		assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.OPEN);
		int requestsWhenOpened = CATALOG.requests().size();

		MockHttpServletResponse addWhileOpen = add(other.id()).andReturn().getResponse();
		MockHttpServletResponse viewWhileOpen = getCart().andReturn().getResponse();

		assertThat(CATALOG.requests()).hasSize(requestsWhenOpened);
		assertThat(addWhileClosed.getStatus()).isEqualTo(503);
		assertSameResponse(addWhileOpen, addWhileClosed);
		assertThat(viewWhileClosed.getStatus()).isEqualTo(200);
		assertThat(viewWhileClosed.getContentAsString()).contains("\"catalogStatus\":\"UNAVAILABLE\"",
				"\"available\":null", "\"currentUnitPrice\":null");
		assertSameResponse(viewWhileOpen, viewWhileClosed);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isEqualTo(1);

		assertThat(output.getOut().lines().filter(line -> line.contains("CallNotPermittedException")))
			.containsExactly(
					lineEndingWith(output, "POST /api/cart/items -> CATALOG_UNAVAILABLE (cause=CallNotPermittedException)"),
					lineEndingWith(output,
							"GET /api/cart -> CATALOG_UNAVAILABLE (cause=CallNotPermittedException, served from snapshot)"));
	}

	@Test
	void bookNotFoundDoesNotOpen() throws Exception {
		for (int i = 0; i < 15; i++) {
			add(UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOK_NOT_AVAILABLE"));
		}

		assertThat(CATALOG.requests()).hasSize(15);
		assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.CLOSED);
		assertThat(catalogCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
		assertThat(catalogCircuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(15);
	}

	@Test
	void outOfStockAndOtherClientErrorsCountAsSuccess() throws Exception {
		Book soldOut = catalog.put(catalog.publish("Tükendi", "77.70").outOfStock());
		for (int i = 0; i < 6; i++) {
			add(soldOut.id()).andExpect(status().isConflict());
		}
		catalog.failWith(Response.problem(400));
		for (int i = 0; i < 6; i++) {
			add(soldOut.id()).andExpect(status().isInternalServerError());
		}

		assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.CLOSED);
		assertThat(catalogCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
	}

	@Test
	void halfOpenClosesAfterSuccessfulTrialCalls(CapturedOutput output) throws Exception {
		clock.fixAt(T1);
		Book book = catalog.publish("Yarı açık", "20.00");
		catalog.failWith(Response.problem(503));
		for (int i = 0; i < 10; i++) {
			add(book.id()).andExpect(status().isServiceUnavailable());
		}
		assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.OPEN);
		catalog.recover();

		clock.advance(Duration.ofSeconds(9));
		add(book.id()).andExpect(status().isServiceUnavailable());
		assertThat(CATALOG.requests()).hasSize(10);

		clock.advance(Duration.ofSeconds(1).plusMillis(1));
		add(book.id()).andExpect(status().isOk());
		assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.HALF_OPEN);
		add(book.id()).andExpect(status().isOk());

		// Başarılı ekleme iki çağrı yapar (kitap + yanıttaki sepet görünümü için lookup): 3 deneme ikinci eklemede dolar.
		assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.CLOSED);
		assertThat(CATALOG.requests()).hasSize(10 + 4);
		assertThat(output.getOut().lines().filter(line -> line.contains("Circuit breaker ")))
			.satisfiesExactly(line -> assertThat(line).endsWith("Circuit breaker catalog CLOSED -> OPEN"),
					line -> assertThat(line).endsWith("Circuit breaker catalog OPEN -> HALF_OPEN"),
					line -> assertThat(line).endsWith("Circuit breaker catalog HALF_OPEN -> CLOSED"));
	}

	@Test
	void halfOpenFailureReopens() throws Exception {
		clock.fixAt(T1);
		Book book = catalog.publish("Yeniden", "20.00");
		catalog.failWith(Response.problem(503));
		for (int i = 0; i < 10; i++) {
			add(book.id()).andExpect(status().isServiceUnavailable());
		}
		clock.advance(Duration.ofSeconds(10).plusMillis(1));

		for (int i = 0; i < 3; i++) {
			add(book.id()).andExpect(status().isServiceUnavailable());
		}
		assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.OPEN);
		add(book.id()).andExpect(status().isServiceUnavailable());
		assertThat(CATALOG.requests()).hasSize(13);
	}

	@Test
	void readinessStaysUpWhileCatalogIsDownAndCircuitIsOpen() throws Exception {
		catalog.failWith(Response.problem(503));
		for (int i = 0; i < 10; i++) {
			add(UUID.randomUUID()).andExpect(status().isServiceUnavailable());
		}
		assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.OPEN);

		for (String path : List.of("/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness")) {
			mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
		}
	}

	private ResultActions getCart() throws Exception {
		return mockMvc.perform(get("/api/cart").with(bearer(token)));
	}

	private ResultActions add(UUID bookId) throws Exception {
		return mockMvc.perform(post("/api/cart/items").with(bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"bookId\":\"" + bookId + "\",\"quantity\":1}"));
	}

	private static void assertSameResponse(MockHttpServletResponse actual, MockHttpServletResponse expected)
			throws Exception {
		assertThat(actual.getStatus()).isEqualTo(expected.getStatus());
		assertThat(actual.getContentType()).isEqualTo(expected.getContentType());
		assertThat(actual.getContentAsString()).isEqualTo(expected.getContentAsString());
	}

	private static String lineEndingWith(CapturedOutput output, String suffix) {
		return output.getOut().lines().filter(line -> line.endsWith(suffix)).findFirst().orElse("<missing> " + suffix);
	}

}
