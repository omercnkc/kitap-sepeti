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

/** Catalog adresinde dinleyen yok (ayrı bağlam: kendi Catalog adresi). Bağlantı reddi ya da connect-timeout, hızlıca 503. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CatalogConnectionRefusedTest {

	private static final int CLOSED_PORT = JwksServer.freePort();

	@Autowired
	private CatalogGateway gateway;

	@Autowired
	private HealthEndpoint healthEndpoint;

	@DynamicPropertySource
	static void catalogProperties(DynamicPropertyRegistry registry) {
		registry.add("app.catalog.base-url", () -> "http://127.0.0.1:" + CLOSED_PORT);
		InternalTestKeys.register(registry);
	}

	@Test
	void closedPortMeansCatalogUnavailableQuickly() {
		long start = System.nanoTime();
		assertThatThrownBy(() -> gateway.requireAvailableBook(UUID.randomUUID()))
			.isInstanceOf(CatalogUnavailableException.class)
			.satisfies(ex -> assertThat(NestedExceptionUtils.getRootCause(ex))
				.isInstanceOfAny(ConnectException.class, SocketTimeoutException.class));
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
		assertThatThrownBy(() -> gateway.lookup(List.of(UUID.randomUUID())))
			.isInstanceOf(CatalogUnavailableException.class);
	}

	@Test
	void healthIsUpWhileNothingListensOnCatalogAddress() {
		assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
		assertThat(healthEndpoint.healthForPath("readiness").getStatus()).isEqualTo(Status.UP);
	}

}
