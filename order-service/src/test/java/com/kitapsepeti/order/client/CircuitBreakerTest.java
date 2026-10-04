package com.kitapsepeti.order.client;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.order.gateway.BookLookupResult;
import com.kitapsepeti.order.gateway.CartSnapshotResult;
import com.kitapsepeti.order.gateway.NotPerformed;
import com.kitapsepeti.order.gateway.PaymentInitiationResult;
import com.kitapsepeti.order.gateway.ReserveResult;
import com.kitapsepeti.order.gateway.StockLine;
import com.kitapsepeti.order.gateway.Unavailable;
import com.kitapsepeti.order.gateway.Unknown;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Servis başına circuit breaker: pencere 20 çağrı, en az 10 çağrı, %50 hata, 10 sn açık, yarı açıkta 3 deneme. Yalnızca
 * teknik hatalar sayılır. Açık kalma süresi uygulama saatiyle ölçülür; test saati ileri sarar.
 */
@ExtendWith(OutputCaptureExtension.class)
class CircuitBreakerTest extends ClientTestSupport {

	private static final String RESERVATIONS = "/internal/stock/reservations";

	private final UUID orderId = UUID.randomUUID();

	private final UUID book = UUID.randomUUID();

	private ReserveResult reserve() {
		return this.catalogGateway.reserve(this.orderId, List.of(new StockLine(this.book, 1)));
	}

	private void catalogReserveReturns(int status, String code) {
		CATALOG.server().stubFor(post(urlEqualTo(RESERVATIONS)).willReturn(problem(status, code)));
	}

	private void catalogReserveHeld() {
		CATALOG.server()
			.stubFor(post(urlEqualTo(RESERVATIONS)).willReturn(json(201, """
					{"orderId":"%s","status":"held","expiresAt":"2026-10-04T10:15:00Z","items":[]}"""
				.formatted(this.orderId))));
	}

	private int catalogRequests() {
		return CATALOG.server().countRequestsMatching(anyRequestedFor(anyUrl()).build()).getCount();
	}

	private void openCatalog() {
		catalogReserveReturns(500, "INTERNAL_ERROR");
		for (int i = 0; i < 10; i++) {
			assertThat(reserve()).isEqualTo(new Unknown());
		}
		assertThat(state(Downstream.CATALOG)).isEqualTo(State.OPEN);
	}

	@Test
	void configurationMatchesSpecification() {
		CircuitBreakerConfig config = this.breakers.get(Downstream.CATALOG).getCircuitBreakerConfig();

		assertThat(config.getSlidingWindowType()).isEqualTo(SlidingWindowType.COUNT_BASED);
		assertThat(config.getSlidingWindowSize()).isEqualTo(20);
		assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
		assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
		assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
		assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(10_000L);
		assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isFalse();
		for (Downstream downstream : Downstream.values()) {
			assertThat(this.breakers.get(downstream).getName()).isEqualTo(downstream.id());
		}
	}

	@Test
	void opensAfterTenTechnicalFailuresAndThenStopsCallingCatalog(CapturedOutput output) {
		catalogReserveReturns(500, "INTERNAL_ERROR");
		for (int i = 0; i < 9; i++) {
			reserve();
		}
		assertThat(state(Downstream.CATALOG)).isEqualTo(State.CLOSED);

		reserve();

		assertThat(state(Downstream.CATALOG)).isEqualTo(State.OPEN);
		assertThat(catalogRequests()).isEqualTo(10);
		assertThat(reserve()).isEqualTo(new NotPerformed());
		assertThat(this.catalogGateway.commit(this.orderId)).isEqualTo(new NotPerformed());
		assertThat(this.catalogGateway.release(this.orderId)).isEqualTo(new NotPerformed());
		assertThat(this.catalogGateway.lookup(List.of(this.book))).isEqualTo(new Unavailable());
		assertThat(catalogRequests()).as("açık devre istek göndermez").isEqualTo(10);
		assertThat(output.getOut()).contains("Circuit breaker catalog CLOSED -> OPEN")
			.contains("Remote call catalog reserve -> NotPerformed (status=-, durationMs=0, cause=CircuitOpen)");
	}

	@Test
	void timeoutsCountAsFailures() {
		CATALOG.server()
			.stubFor(get(urlPathEqualTo("/api/books/lookup")).willReturn(slow(json(200, "{\"items\":[]}"))));

		this.catalogGateway.lookup(List.of(this.book));

		assertThat(this.breakers.get(Downstream.CATALOG).getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
	}

	@Test
	void businessProblemsDoNotOpen() {
		catalogReserveReturns(409, "INSUFFICIENT_STOCK");
		for (int i = 0; i < 12; i++) {
			assertThat(reserve()).isInstanceOf(ReserveResult.Insufficient.class);
		}
		catalogReserveReturns(400, "VALIDATION_FAILED");
		for (int i = 0; i < 12; i++) {
			reserve();
		}

		assertThat(state(Downstream.CATALOG)).isEqualTo(State.CLOSED);
		assertThat(this.breakers.get(Downstream.CATALOG).getMetrics().getNumberOfFailedCalls()).isZero();
		assertThat(catalogRequests()).isEqualTo(24);
	}

	@Test
	void failureRateBelowThresholdKeepsItClosed() {
		catalogReserveHeld();
		for (int i = 0; i < 6; i++) {
			reserve();
		}
		catalogReserveReturns(503, "INTERNAL_ERROR");
		for (int i = 0; i < 5; i++) {
			reserve();
		}

		assertThat(state(Downstream.CATALOG)).isEqualTo(State.CLOSED);
	}

	@Test
	void halfOpenSuccessesCloseAfterOpenDuration(CapturedOutput output) {
		openCatalog();
		catalogReserveHeld();

		this.clock.advance(Duration.ofSeconds(9));
		assertThat(reserve()).isEqualTo(new NotPerformed());
		assertThat(catalogRequests()).isEqualTo(10);

		this.clock.advance(Duration.ofSeconds(1).plusMillis(1));
		for (int i = 0; i < 3; i++) {
			assertThat(reserve()).isInstanceOf(ReserveResult.Reserved.class);
		}

		assertThat(state(Downstream.CATALOG)).isEqualTo(State.CLOSED);
		assertThat(catalogRequests()).isEqualTo(13);
		assertThat(output.getOut()).contains("Circuit breaker catalog OPEN -> HALF_OPEN")
			.contains("Circuit breaker catalog HALF_OPEN -> CLOSED");
	}

	@Test
	void halfOpenFailureReopens() {
		openCatalog();
		this.clock.advance(Duration.ofSeconds(11));

		for (int i = 0; i < 3; i++) {
			assertThat(reserve()).isEqualTo(new Unknown());
		}

		assertThat(state(Downstream.CATALOG)).isEqualTo(State.OPEN);
	}

	@Test
	void instancesAreIndependent() {
		openCatalog();
		CART.server()
			.stubFor(post(urlEqualTo("/internal/cart/snapshot"))
				.willReturn(json(200, "{\"cartId\":null,\"updatedAt\":null,\"items\":[]}")));
		PAYMENT.server()
			.stubFor(post(urlEqualTo("/internal/payments")).willReturn(json(201, """
					{"paymentId":"%s","orderId":"%s","status":"initiated"}""".formatted(UUID.randomUUID(), this.orderId))));

		assertThat(this.cartGateway.snapshot(UUID.randomUUID())).isEqualTo(new CartSnapshotResult.Empty());
		assertThat(this.paymentGateway.initiate(this.orderId, UUID.randomUUID(), BigDecimal.TEN, "TRY"))
			.isInstanceOf(PaymentInitiationResult.Initiated.class);
		assertThat(state(Downstream.CART)).isEqualTo(State.CLOSED);
		assertThat(state(Downstream.PAYMENT)).isEqualTo(State.CLOSED);
		assertThat(this.catalogGateway.lookup(List.of(this.book))).isEqualTo(new Unavailable());
		assertThat(this.catalogGateway.lookup(List.of())).isInstanceOf(BookLookupResult.Found.class);
	}

}
