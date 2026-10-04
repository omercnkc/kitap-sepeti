package com.kitapsepeti.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestPathMaskerTest {

	private static final String ID = UUID.randomUUID().toString();

	private final RequestPathMasker masker = RequestPathMasker.of(
			"/api/cart/items/{bookId}",
			"/internal/stock/reservations/{orderId}",
			"/internal/stock/reservations/{orderId}/commit",
			"/internal/stock/reservations/{orderId}/release",
			"/api/books/{bookId}",
			"/api/books/lookup");

	@Test
	void templateMasksVariableSegmentWhetherOrNotItIsAUuid() {
		assertThat(masker.mask("/api/cart/items/" + ID)).isEqualTo("/api/cart/items/:bookId");
		assertThat(masker.mask("/api/cart/items/abc")).isEqualTo("/api/cart/items/:bookId");
		assertThat(masker.mask("/api/cart/items/" + ID.toUpperCase())).isEqualTo("/api/cart/items/:bookId");
		assertThat(masker.mask("/internal/stock/reservations/" + ID)).isEqualTo("/internal/stock/reservations/:orderId");
	}

	@Test
	void multiSegmentTemplatesKeepTheirLiteralTail() {
		assertThat(masker.mask("/internal/stock/reservations/" + ID + "/commit"))
			.isEqualTo("/internal/stock/reservations/:orderId/commit");
		assertThat(masker.mask("/internal/stock/reservations/abc/release"))
			.isEqualTo("/internal/stock/reservations/:orderId/release");
	}

	@Test
	void patternsAreReturnedInRegistrationOrder() {
		assertThat(masker.patterns()).containsExactly("/api/cart/items/{bookId}", "/internal/stock/reservations/{orderId}",
				"/internal/stock/reservations/{orderId}/commit", "/internal/stock/reservations/{orderId}/release",
				"/api/books/{bookId}", "/api/books/lookup");
		assertThat(RequestPathMasker.uuidOnly().patterns()).isEmpty();
	}

	@Test
	void coversMappingsWithTheSameShapeRegardlessOfVariableNameOrRegex() {
		assertThat(masker.covers("/api/books/{id}")).isTrue();
		assertThat(masker.covers("/api/books/{id:[0-9a-f-]+}")).isTrue();
		assertThat(masker.covers("/internal/stock/reservations/{x}/commit")).isTrue();

		assertThat(masker.covers("/internal/stock/reservations/{x}/refund")).isFalse();
		assertThat(masker.covers("/api/books/{id}/reviews")).isFalse();
		assertThat(masker.covers("/api/authors/{id}")).isFalse();
		assertThat(masker.covers("/api/books/{*rest}")).isFalse();
		assertThat(masker.covers("/api/cart/items/fixed")).isFalse();
		assertThat(RequestPathMasker.uuidOnly().covers("/api/books/{id}")).isFalse();
	}

	@Test
	void moreSpecificTemplateWins() {
		assertThat(masker.mask("/api/books/lookup")).isEqualTo("/api/books/lookup");
		assertThat(masker.mask("/api/books/" + ID)).isEqualTo("/api/books/:bookId");
		assertThat(masker.mask("/api/books/some-slug")).isEqualTo("/api/books/:bookId");
	}

	@Test
	void pathsOutsideTemplatesMaskOnlyUuidSegments() {
		String upper = UUID.randomUUID().toString().toUpperCase();
		assertThat(masker.mask("/internal/stock/reservations/" + ID + "/unknown"))
			.isEqualTo("/internal/stock/reservations/:id/unknown");
		assertThat(masker.mask("/api/other/" + upper + "/x/" + ID)).isEqualTo("/api/other/:id/x/:id");
		assertThat(RequestPathMasker.uuidOnly().mask("/api/cart/items/" + upper)).isEqualTo("/api/cart/items/:id");
		assertThat(RequestPathMasker.uuidOnly().mask("/api/cart/items/abc")).isEqualTo("/api/cart/items/abc");
	}

	@Test
	void nonUuidSegmentsOutsideTemplatesStayAsTheyAre() {
		assertThat(RequestPathMasker.uuidOnly().mask("/api/books/lookup")).isEqualTo("/api/books/lookup");
		assertThat(masker.mask("/api/admin/ping")).isEqualTo("/api/admin/ping");
		assertThat(masker.mask("/api/cart/items")).isEqualTo("/api/cart/items");
		assertThat(masker.mask("/")).isEqualTo("/");
		// UUID bir segmentin yalnızca parçasıysa segment UUID biçiminde değildir.
		assertThat(masker.mask("/api/x/" + ID + ".json")).isEqualTo("/api/x/" + ID + ".json");
	}

	@Test
	void trailingSlashIsKeptAndDoesNotBreakTheMatch() {
		assertThat(masker.mask("/api/cart/items/abc/")).isEqualTo("/api/cart/items/:bookId/");
		assertThat(masker.mask("/api/other/" + ID + "/")).isEqualTo("/api/other/:id/");
	}

	@Test
	void queryStringAndFragmentNeverAppear() {
		assertThat(masker.mask("/api/cart/items/abc?token=" + ID)).isEqualTo("/api/cart/items/:bookId");
		assertThat(masker.mask("/api/x?q=1#frag")).isEqualTo("/api/x");

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/books/lookup");
		request.setQueryString("ids=" + ID);
		assertThat(masker.mask(request)).isEqualTo("/api/books/lookup");
	}

	@Test
	void resultIsAlwaysAValidUri() {
		for (String raw : new String[] { "/a b|c", "/api/{x}", "/api/%zz/%4", "/api/ç ğ/\"<>", "/api/%41ok",
				"/api/" + ID + "/[x]" }) {
			String masked = RequestPathMasker.uuidOnly().mask(raw);
			assertThat(URI.create(masked).getRawPath()).as(raw).isEqualTo(masked);
		}
		assertThat(RequestPathMasker.uuidOnly().mask("/a b|c")).isEqualTo("/a%20b%7Cc");
		assertThat(RequestPathMasker.uuidOnly().mask("/api/%41ok")).isEqualTo("/api/%41ok");
		assertThat(RequestPathMasker.uuidOnly().mask("/api/%zz")).isEqualTo("/api/%25zz");
		assertThat(masker.mask("/api/cart/items/a b")).isEqualTo("/api/cart/items/:bookId");
	}

	@Test
	void requestVariantKeepsContextPathAndMasksTheRest() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shop/api/cart/items/" + ID);
		request.setContextPath("/shop");
		assertThat(masker.mask(request)).isEqualTo("/shop/api/cart/items/:bookId");

		MockHttpServletRequest noUri = new MockHttpServletRequest();
		noUri.setRequestURI(null);
		assertThat(masker.mask(noUri)).isNull();
		assertThat(masker.mask((String) null)).isNull();
	}

	@Test
	void invalidPatternsAreRejected() {
		for (String pattern : new String[] { "api/x", "/api/x/", "/api//x", "/api/*", "/api/{id}.json", "/api/x?y",
				"/api/{a}/{a}", "/api/{1x}" }) {
			assertThatIllegalArgumentException().as(pattern).isThrownBy(() -> RequestPathMasker.of(pattern));
		}
		assertThatIllegalArgumentException().isThrownBy(() -> RequestPathMasker.of("/api/x/{id}", "/api/x/{id}"));
	}

}
