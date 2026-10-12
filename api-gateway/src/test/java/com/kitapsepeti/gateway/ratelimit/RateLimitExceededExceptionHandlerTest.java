package com.kitapsepeti.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.client.HttpClientErrorException;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

class RateLimitExceededExceptionHandlerTest {

	private RateLimitExceededExceptionHandler handler;

	@BeforeEach
	void setUp() {
		this.handler = new RateLimitExceededExceptionHandler(Optional.of(JsonMapper.builder().build()), 60);
	}

	@Test
	void tooManyRequests_writesProblemDetailsAndRetryAfter() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/auth/login").build());
		HttpClientErrorException ex = HttpClientErrorException.create(
				HttpStatus.TOO_MANY_REQUESTS,
				"Too Many Requests",
				new HttpHeaders(),
				null,
				StandardCharsets.UTF_8);

		StepVerifier.create(this.handler.handle(exchange, ex)).verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(exchange.getResponse().getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");

		String body = exchange.getResponse().getBodyAsString().block();
		assertThat(body).isNotBlank();
		assertThat(body).contains("\"status\":429");
		assertThat(body).contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
		assertThat(body).contains("\"title\":\"İstek sınırı aşıldı\"");
		assertThat(body).contains("\"type\":\"about:blank\"");
		assertThat(body).contains("\"instance\":\"/api/auth/login\"");
		assertThat(body).contains("\"detail\":");
	}

	@Test
	void non429_propagatesError() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/cart").build());
		RuntimeException ex = new RuntimeException("other");

		StepVerifier.create(this.handler.handle(exchange, ex))
				.expectErrorMatches(err -> err == ex)
				.verify();

		assertThat(exchange.getResponse().getStatusCode()).isNull();
	}

	@Test
	void customRetryAfterSeconds_isHonored() {
		RateLimitExceededExceptionHandler custom =
				new RateLimitExceededExceptionHandler(Optional.of(JsonMapper.builder().build()), 15);
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.post("/api/orders/checkout").build());
		HttpClientErrorException ex = HttpClientErrorException.create(
				HttpStatus.TOO_MANY_REQUESTS,
				"Too Many Requests",
				new HttpHeaders(),
				null,
				StandardCharsets.UTF_8);

		StepVerifier.create(custom.handle(exchange, ex)).verifyComplete();

		assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("15");
		assertThat(exchange.getResponse().getBodyAsString().block())
				.contains("\"instance\":\"/api/orders/checkout\"");
	}

}
