package com.kitapsepeti.order.client;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.kitapsepeti.order.gateway.BookLookupResult;
import com.kitapsepeti.order.gateway.CatalogBook;
import com.kitapsepeti.order.gateway.CommitResult;
import com.kitapsepeti.order.gateway.Rejected;
import com.kitapsepeti.order.gateway.ReleaseResult;
import com.kitapsepeti.order.gateway.ReservationStatus;
import com.kitapsepeti.order.gateway.ReserveResult;
import com.kitapsepeti.order.gateway.StockLine;
import com.kitapsepeti.order.gateway.Unavailable;
import com.kitapsepeti.order.gateway.Unknown;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Catalog kitap okuma ve stok rezervasyonu eşlemesi (docs/api/catalog-internal-stock.md). */
class CatalogGatewayTest extends ClientTestSupport {

	private static final String RESERVATIONS = "/internal/stock/reservations";

	private final UUID orderId = UUID.randomUUID();

	private final UUID book1 = UUID.randomUUID();

	private final UUID book2 = UUID.randomUUID();

	private final Instant expiresAt = Instant.parse("2026-10-04T10:15:00Z");

	private String reservation(UUID order, String status) {
		return """
				{"orderId":"%s","status":"%s","expiresAt":"%s","newField":1,
				 "items":[{"bookId":"%s","title":"A","quantity":2,"unitPrice":149.90,"currency":"TRY"}]}"""
			.formatted(order, status, this.expiresAt, this.book1);
	}

	private String commitPath() {
		return RESERVATIONS + "/" + this.orderId + "/commit";
	}

	private String releasePath() {
		return RESERVATIONS + "/" + this.orderId + "/release";
	}

	@Nested
	class Lookup {

		private void respond(ResponseDefinitionBuilder response) {
			CATALOG.server().stubFor(get(urlPathEqualTo("/api/books/lookup")).willReturn(response));
		}

		private BookLookupResult lookup() {
			return catalogGateway.lookup(List.of(book1, book2, book1));
		}

		@Test
		void foundAndMissingBooksAreSeparatedAndKeyIsNotSentToPublicEndpoint() {
			UUID unrequested = UUID.randomUUID();
			respond(json(200, """
					{"items":[
					 {"id":"%s","title":"A","priceAmount":149.90,"currency":"TRY","inStock":true,"authors":[],"publisher":null,"slug":"a"},
					 {"id":"%s","title":"X","priceAmount":1,"currency":"TRY","inStock":true,"authors":[],"publisher":null}]}"""
				.formatted(book1, unrequested)));

			BookLookupResult result = lookup();

			assertThat(result).isEqualTo(new BookLookupResult.Found(
					Map.of(book1, new CatalogBook(book1, "A", new BigDecimal("149.90"), "TRY", true)), Set.of(book2)));
			assertThat(((BookLookupResult.Found) result).sellable(book1)).isPresent();
			assertThat(((BookLookupResult.Found) result).sellable(book2)).isEmpty();
			List<LoggedRequest> requests = CATALOG.server().findAll(getRequestedFor(urlPathEqualTo("/api/books/lookup")));
			assertThat(requests).singleElement().satisfies(request -> {
				assertThat(request.queryParameter("ids").values()).containsExactly(book1.toString(), book2.toString());
				assertThat(request.containsHeader(InternalApiKey.HEADER)).isFalse();
			});
		}

		@Test
		void outOfStockBookIsFoundButNotSellable() {
			respond(json(200, """
					{"items":[{"id":"%s","title":"A","priceAmount":5,"currency":"TRY","inStock":false,"authors":[],"publisher":null}]}"""
				.formatted(book1)));

			BookLookupResult.Found found = (BookLookupResult.Found) catalogGateway.lookup(List.of(book1));

			assertThat(found.books().get(book1).inStock()).isFalse();
			assertThat(found.sellable(book1)).isEmpty();
			assertThat(found.notFound()).isEmpty();
		}

		@Test
		void emptyInputDoesNotCallCatalog() {
			assertThat(catalogGateway.lookup(List.of())).isEqualTo(new BookLookupResult.Found(Map.of(), Set.of()));
			CATALOG.server().verify(0, anyRequestedFor(anyUrl()));
		}

		@Test
		void moreThanFiftyBooksIsRejectedLocally() {
			List<UUID> ids = IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID()).toList();

			assertThatThrownBy(() -> catalogGateway.lookup(ids)).isInstanceOf(IllegalArgumentException.class);
			CATALOG.server().verify(0, anyRequestedFor(anyUrl()));
		}

		@ParameterizedTest
		@ValueSource(strings = { "VALIDATION_FAILED", "MALFORMED_REQUEST" })
		void problemsAreUnavailable(String code) {
			respond(problem(400, code));

			assertThat(lookup()).isEqualTo(new Unavailable());
		}

		@ParameterizedTest
		@ValueSource(ints = { 500, 503 })
		void serverErrorsAreUnavailable(int status) {
			respond(problem(status, "INTERNAL_ERROR"));

			assertThat(lookup()).isEqualTo(new Unavailable());
		}

		@Test
		void readTimeoutIsUnavailable() {
			respond(slow(json(200, "{\"items\":[]}")));

			assertThat(lookup()).isEqualTo(new Unavailable());
		}

		@ParameterizedTest
		@ValueSource(strings = { "{not json", "{}", "{\"items\":[{\"id\":\"%s\",\"title\":\"A\",\"priceAmount\":1,\"currency\":\"TRY\"}]}",
				"{\"items\":[{\"id\":\"%s\",\"title\":\"A\",\"currency\":\"TRY\",\"inStock\":true}]}",
				"{\"items\":[{\"id\":\"%1$s\",\"title\":\"A\",\"priceAmount\":1,\"currency\":\"TRY\",\"inStock\":true},{\"id\":\"%1$s\",\"title\":\"A\",\"priceAmount\":1,\"currency\":\"TRY\",\"inStock\":true}]}" })
		void malformedBodyIsUnavailable(String body) {
			respond(json(200, body.formatted(book1)));

			assertThat(lookup()).isEqualTo(new Unavailable());
		}

	}

	@Nested
	class Reserve {

		private void respond(ResponseDefinitionBuilder response) {
			CATALOG.server().stubFor(post(urlEqualTo(RESERVATIONS)).willReturn(response));
		}

		private ReserveResult reserve() {
			return catalogGateway.reserve(orderId, List.of(new StockLine(book1, 2), new StockLine(book2, 1)));
		}

		@Test
		void createdHeldReservationIsReservedAndBodyMatchesContract() {
			respond(json(201, reservation(orderId, "held")));

			assertThat(reserve()).isEqualTo(new ReserveResult.Reserved(expiresAt));
			CATALOG.server()
				.verify(1, postRequestedFor(urlEqualTo(RESERVATIONS)).withHeader(InternalApiKey.HEADER, equalTo(API_KEY))
					.withRequestBody(equalToJson("""
							{"orderId":"%s","items":[{"bookId":"%s","quantity":2},{"bookId":"%s","quantity":1}]}"""
						.formatted(orderId, book1, book2))));
		}

		@Test
		void replayedHeldReservationIsReserved() {
			respond(json(200, reservation(orderId, "held")));

			assertThat(reserve()).isEqualTo(new ReserveResult.Reserved(expiresAt));
		}

		@ParameterizedTest
		@ValueSource(strings = { "committed", "released" })
		void replayedFinishedReservationIsNotHeld(String status) {
			respond(json(200, reservation(orderId, status)));

			assertThat(reserve())
				.isEqualTo(new ReserveResult.NotHeld(ReservationStatus.valueOf(status.toUpperCase(Locale.ROOT))));
		}

		@Test
		void insufficientStockCarriesBookIds() {
			respond(stockProblem("INSUFFICIENT_STOCK", book2));

			assertThat(reserve()).isEqualTo(new ReserveResult.Insufficient(List.of(book2)));
		}

		@Test
		void unavailableBookCarriesBookIds() {
			respond(stockProblem("BOOK_NOT_AVAILABLE", book1, book2));

			assertThat(reserve()).isEqualTo(new ReserveResult.NotSellable(List.of(book1, book2)));
		}

		@Test
		void mismatchedReplayIsRejected() {
			respond(problem(409, "RESERVATION_MISMATCH"));

			assertThat(reserve()).isEqualTo(new Rejected(409, "RESERVATION_MISMATCH"));
		}

		@Test
		void validationAndAuthProblemsAreRejected() {
			respond(problem(400, "VALIDATION_FAILED"));
			assertThat(reserve()).isEqualTo(new Rejected(400, "VALIDATION_FAILED"));

			respond(problem(401, "UNAUTHORIZED").withHeader("WWW-Authenticate", "ApiKey realm=\"internal\""));
			assertThat(reserve()).isEqualTo(new Rejected(401, "UNAUTHORIZED"));
		}

		@Test
		void problemWithoutReadableBodyIsRejectedWithoutCode() {
			respond(json(409, "<html>conflict</html>"));

			assertThat(reserve()).isEqualTo(new Rejected(409, null));
		}

		@ParameterizedTest
		@ValueSource(ints = { 500, 503 })
		void serverErrorsAreUnknown(int status) {
			respond(problem(status, "INTERNAL_ERROR"));

			assertThat(reserve()).isEqualTo(new Unknown());
		}

		@Test
		void readTimeoutIsUnknown() {
			respond(slow(json(201, reservation(orderId, "held"))));

			assertThat(reserve()).isEqualTo(new Unknown());
		}

		@Test
		void malformedOrForeignOrUnknownStatusIsUnknown() {
			for (String body : List.of("{not json", "{\"orderId\":\"" + orderId + "\",\"status\":\"held\"}",
					reservation(UUID.randomUUID(), "held"), reservation(orderId, "mixed"))) {
				respond(json(201, body));
				assertThat(reserve()).isEqualTo(new Unknown());
			}
		}

		@Test
		void invalidLinesAreRejectedLocally() {
			assertThatThrownBy(() -> catalogGateway.reserve(orderId, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> catalogGateway.reserve(orderId,
					List.of(new StockLine(book1, 1), new StockLine(book1, 2))))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> catalogGateway.reserve(orderId, List.of(new StockLine(book1, 0))))
				.isInstanceOf(IllegalArgumentException.class);
			CATALOG.server().verify(0, anyRequestedFor(anyUrl()));
		}

	}

	@Nested
	class Commit {

		private void respond(ResponseDefinitionBuilder response) {
			CATALOG.server().stubFor(post(urlEqualTo(commitPath())).willReturn(response));
		}

		@Test
		void committedReservationIsCommittedWithoutRequestBody() {
			respond(json(200, reservation(orderId, "committed")));

			assertThat(catalogGateway.commit(orderId)).isEqualTo(new CommitResult.Committed());
			CATALOG.server()
				.verify(1, postRequestedFor(urlEqualTo(commitPath())).withHeader(InternalApiKey.HEADER, equalTo(API_KEY))
					.withHeader("Authorization", absent()));
			assertThat(CATALOG.server().findAll(postRequestedFor(urlEqualTo(commitPath()))).getFirst().getBody())
				.isEmpty();
		}

		@Test
		void releasedReservationIsAlreadyReleased() {
			respond(problem(409, "RESERVATION_RELEASED"));

			assertThat(catalogGateway.commit(orderId)).isEqualTo(new CommitResult.AlreadyReleased());
		}

		@Test
		void missingReservationIsRejected() {
			respond(problem(404, "RESOURCE_NOT_FOUND"));

			assertThat(catalogGateway.commit(orderId)).isEqualTo(new Rejected(404, "RESOURCE_NOT_FOUND"));
		}

		@Test
		void unexpectedStateOrForeignOrderIsUnknown() {
			respond(json(200, reservation(orderId, "held")));
			assertThat(catalogGateway.commit(orderId)).isEqualTo(new Unknown());

			respond(json(200, reservation(UUID.randomUUID(), "committed")));
			assertThat(catalogGateway.commit(orderId)).isEqualTo(new Unknown());
		}

		@Test
		void serverErrorAndTimeoutAreUnknown() {
			respond(problem(500, "INTERNAL_ERROR"));
			assertThat(catalogGateway.commit(orderId)).isEqualTo(new Unknown());

			respond(slow(json(200, reservation(orderId, "committed"))));
			assertThat(catalogGateway.commit(orderId)).isEqualTo(new Unknown());
		}

	}

	@Nested
	class Release {

		private void respond(ResponseDefinitionBuilder response) {
			CATALOG.server().stubFor(post(urlEqualTo(releasePath())).willReturn(response));
		}

		@Test
		void releasedReservationIsReleased() {
			respond(json(200, reservation(orderId, "released")));

			assertThat(catalogGateway.release(orderId)).isEqualTo(new ReleaseResult.Released());
			CATALOG.server()
				.verify(1, postRequestedFor(urlEqualTo(releasePath())).withHeader(InternalApiKey.HEADER, equalTo(API_KEY)));
		}

		/** Rezervasyon yoksa (reserve Catalog'a hiç ulaşmadı) tutulan stok da yok. */
		@Test
		void unknownReservationIsReleased() {
			respond(problem(404, "RESOURCE_NOT_FOUND"));

			assertThat(catalogGateway.release(orderId)).isEqualTo(new ReleaseResult.Released());
		}

		/** Yanlış adres/yol 404'ü ProblemDetail koduyla ayrışır; "rezervasyon yok" sayılmaz. */
		@Test
		void notFoundWithoutResourceCodeIsRejected() {
			respond(problem(404, "NOT_FOUND"));
			assertThat(catalogGateway.release(orderId)).isEqualTo(new Rejected(404, "NOT_FOUND"));

			respond(json(404, "not found"));
			assertThat(catalogGateway.release(orderId)).isEqualTo(new Rejected(404, null));
		}

		@Test
		void committedReservationIsAlreadyCommitted() {
			respond(problem(409, "RESERVATION_COMMITTED"));

			assertThat(catalogGateway.release(orderId)).isEqualTo(new ReleaseResult.AlreadyCommitted());
		}

		@Test
		void unexpectedStateIsUnknown() {
			respond(json(200, reservation(orderId, "committed")));

			assertThat(catalogGateway.release(orderId)).isEqualTo(new Unknown());
		}

		@Test
		void serverErrorAndTimeoutAreUnknown() {
			respond(problem(503, "INTERNAL_ERROR"));
			assertThat(catalogGateway.release(orderId)).isEqualTo(new Unknown());

			respond(slow(json(200, reservation(orderId, "released"))));
			assertThat(catalogGateway.release(orderId)).isEqualTo(new Unknown());
		}

	}

}
