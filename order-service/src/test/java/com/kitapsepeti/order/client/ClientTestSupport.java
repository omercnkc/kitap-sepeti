package com.kitapsepeti.order.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.kitapsepeti.order.ApiTestSupport;
import com.kitapsepeti.order.gateway.CartGateway;
import com.kitapsepeti.order.gateway.CatalogGateway;
import com.kitapsepeti.order.gateway.PaymentGateway;
import com.kitapsepeti.order.support.MutableClock;
import com.kitapsepeti.order.support.StubServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Gateway testlerinin tabanı: gerçek Feign istemcileri (Spring bağlamındaki yapılandırmayla) WireMock sunucularına
 * gider. Her testten önce sunucular açık ve boş, circuit breaker'lar kapalı ve sıfır, saat gerçek.
 */
abstract class ClientTestSupport extends ApiTestSupport {

	/** application-test.yml'deki sahte anahtar. */
	static final String API_KEY = "order-test-internal-key-not-a-secret";

	static final List<StubServer> STUBS = List.of(CART, CATALOG, PAYMENT);

	@Autowired
	CartGateway cartGateway;

	@Autowired
	CatalogGateway catalogGateway;

	@Autowired
	PaymentGateway paymentGateway;

	@Autowired
	DownstreamCircuitBreakers breakers;

	@Autowired
	MutableClock clock;

	@BeforeEach
	void resetRemotes() {
		for (StubServer stub : STUBS) {
			stub.ensureRunning();
			stub.server().resetAll();
		}
		for (Downstream downstream : Downstream.values()) {
			this.breakers.get(downstream).reset();
		}
		this.clock.reset();
	}

	@AfterEach
	void restoreRemotes() {
		STUBS.forEach(StubServer::ensureRunning);
		this.clock.reset();
	}

	CircuitBreaker.State state(Downstream downstream) {
		return this.breakers.get(downstream).getState();
	}

	static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

	/** Sağlayıcıların ortak ProblemDetail gövdesi (common GlobalExceptionHandler biçimi). */
	static ResponseDefinitionBuilder problem(int status, String code) {
		return aResponse().withStatus(status)
			.withHeader("Content-Type", "application/problem+json")
			.withBody("""
					{"type":"about:blank","title":"t","status":%d,"code":"%s","instance":"/x","traceId":"abc"}"""
				.formatted(status, code));
	}

	static ResponseDefinitionBuilder stockProblem(String code, UUID... bookIds) {
		StringBuilder ids = new StringBuilder();
		for (UUID id : bookIds) {
			ids.append(ids.isEmpty() ? "" : ",").append('"').append(id).append('"');
		}
		return aResponse().withStatus(409)
			.withHeader("Content-Type", "application/problem+json")
			.withBody("""
					{"title":"t","status":409,"code":"%s","instance":"/internal/stock/reservations","bookIds":[%s]}"""
				.formatted(code, ids));
	}

	/** Feign okuma zaman aşımı 3 sn; bu gecikme onu aşar. */
	static ResponseDefinitionBuilder slow(ResponseDefinitionBuilder response) {
		return response.withFixedDelay(3_500);
	}

}
