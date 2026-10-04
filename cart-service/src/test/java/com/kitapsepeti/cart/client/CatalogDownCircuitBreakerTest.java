package com.kitapsepeti.cart.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.cart.TestcontainersConfiguration;
import com.kitapsepeti.cart.exception.CatalogUnavailableException;
import com.kitapsepeti.cart.support.InternalTestKeys;
import com.kitapsepeti.cart.support.JwksServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Catalog adresinde dinleyen yok: bağlantı hataları devreyi açar, açıkken ağa çıkılmaz, readiness UP kalır. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CatalogDownCircuitBreakerTest {

	private static final int CLOSED_PORT = JwksServer.freePort();

	@Autowired
	private CatalogGateway gateway;

	@Autowired
	private CircuitBreaker catalogCircuitBreaker;

	@Autowired
	private HealthEndpoint healthEndpoint;

	@DynamicPropertySource
	static void catalogProperties(DynamicPropertyRegistry registry) {
		registry.add("app.catalog.base-url", () -> "http://127.0.0.1:" + CLOSED_PORT);
		InternalTestKeys.register(registry);
	}

	@Test
	void connectionFailuresOpenTheCircuitAndReadinessStaysUp() {
		catalogCircuitBreaker.reset();
		for (int i = 0; i < 10; i++) {
			assertThatThrownBy(() -> gateway.lookup(List.of(UUID.randomUUID())))
				.isInstanceOf(CatalogUnavailableException.class)
				.satisfies(ex -> assertThat(NestedExceptionUtils.getRootCause(ex))
					.isInstanceOfAny(ConnectException.class, SocketTimeoutException.class));
		}
		assertThat(catalogCircuitBreaker.getState()).isEqualTo(State.OPEN);

		long start = System.nanoTime();
		assertThatThrownBy(() -> gateway.requireAvailableBook(UUID.randomUUID()))
			.isInstanceOf(CatalogUnavailableException.class)
			.cause()
			.isInstanceOf(CallNotPermittedException.class);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(100));
		assertThat(catalogCircuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);

		assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
		assertThat(healthEndpoint.healthForPath("readiness").getStatus()).isEqualTo(Status.UP);
	}

}
