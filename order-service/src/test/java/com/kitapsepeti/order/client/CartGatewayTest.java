package com.kitapsepeti.order.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.kitapsepeti.order.gateway.CartSnapshotResult;
import com.kitapsepeti.order.gateway.StockLine;
import com.kitapsepeti.order.gateway.Unavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Cart {@code POST /internal/cart/snapshot} eşlemesi. Okuma: her hata {@link Unavailable}. */
class CartGatewayTest extends ClientTestSupport {

	private static final String PATH = "/internal/cart/snapshot";

	private final UUID userId = UUID.randomUUID();

	private final UUID cartId = UUID.randomUUID();

	private final UUID book1 = UUID.randomUUID();

	private final UUID book2 = UUID.randomUUID();

	@Test
	void activeCartIsMappedInOrderAndRequestCarriesOnlyUserIdAndKey() {
		respond(json(200, """
				{"cartId":"%s","updatedAt":"2026-10-04T10:00:00Z","futureField":{"x":1},"items":[
				 {"bookId":"%s","quantity":2,"unitPriceSnapshot":149.90,"currency":"TRY","title":"A"},
				 {"bookId":"%s","quantity":1,"unitPriceSnapshot":10.00,"currency":"TRY","title":"B","extra":true}]}"""
			.formatted(this.cartId, this.book1, this.book2)));

		CartSnapshotResult result = this.cartGateway.snapshot(this.userId);

		assertThat(result).isEqualTo(new CartSnapshotResult.Snapshot(this.cartId,
				List.of(new StockLine(this.book1, 2), new StockLine(this.book2, 1))));
		CART.server()
			.verify(1, postRequestedFor(urlEqualTo(PATH))
				.withHeader(InternalApiKey.HEADER, equalTo(API_KEY))
				.withHeader("Content-Type", equalTo("application/json"))
				.withRequestBody(equalToJson("{\"userId\":\"" + this.userId + "\"}")));
	}

	@Test
	void noActiveCartIsEmpty() {
		respond(json(200, "{\"cartId\":null,\"updatedAt\":null,\"items\":[]}"));

		assertThat(this.cartGateway.snapshot(this.userId)).isEqualTo(new CartSnapshotResult.Empty());
	}

	@Test
	void emptyActiveCartIsEmpty() {
		respond(json(200, "{\"cartId\":\"" + this.cartId + "\",\"updatedAt\":\"2026-10-04T10:00:00Z\",\"items\":[]}"));

		assertThat(this.cartGateway.snapshot(this.userId)).isEqualTo(new CartSnapshotResult.Empty());
	}

	@ParameterizedTest
	@ValueSource(strings = { "VALIDATION_FAILED", "MALFORMED_REQUEST", "UNAUTHORIZED" })
	void problemsAreUnavailable(String code) {
		respond(problem(code.equals("UNAUTHORIZED") ? 401 : 400, code));

		assertThat(this.cartGateway.snapshot(this.userId)).isEqualTo(new Unavailable());
	}

	@ParameterizedTest
	@ValueSource(ints = { 500, 502, 503 })
	void serverErrorsAreUnavailable(int status) {
		respond(problem(status, "INTERNAL_ERROR"));

		assertThat(this.cartGateway.snapshot(this.userId)).isEqualTo(new Unavailable());
	}

	@Test
	void readTimeoutIsUnavailable() {
		respond(slow(json(200, "{\"cartId\":null,\"updatedAt\":null,\"items\":[]}")));

		assertThat(this.cartGateway.snapshot(this.userId)).isEqualTo(new Unavailable());
	}

	@Test
	void connectionResetIsUnavailable() {
		respond(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

		assertThat(this.cartGateway.snapshot(this.userId)).isEqualTo(new Unavailable());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{not json",
			"",
			"{\"cartId\":null,\"updatedAt\":null}",
			"{\"updatedAt\":null,\"items\":[]}",
			"{\"cartId\":null,\"items\":[{\"quantity\":1}]}",
			"{\"cartId\":null,\"items\":[{\"bookId\":\"%2$s\"}]}",
			"{\"cartId\":null,\"items\":[{\"bookId\":\"%2$s\",\"quantity\":1}]}",
			"{\"cartId\":\"%1$s\",\"items\":[{\"bookId\":\"%2$s\",\"quantity\":0}]}",
			"{\"cartId\":\"%1$s\",\"items\":[{\"bookId\":\"%2$s\",\"quantity\":100}]}",
			"{\"cartId\":\"%1$s\",\"items\":[{\"bookId\":\"%2$s\",\"quantity\":1},{\"bookId\":\"%2$s\",\"quantity\":2}]}",
			"{\"cartId\":\"%1$s\",\"items\":[null]}",
			"{\"cartId\":\"not-a-uuid\",\"items\":[]}" })
	void malformedOrContractBreakingBodyIsUnavailable(String body) {
		respond(json(200, body.formatted(this.cartId, this.book1)));

		assertThat(this.cartGateway.snapshot(this.userId)).isEqualTo(new Unavailable());
	}

	private static void respond(ResponseDefinitionBuilder response) {
		CART.server().stubFor(post(urlEqualTo(PATH)).willReturn(response));
	}

}
