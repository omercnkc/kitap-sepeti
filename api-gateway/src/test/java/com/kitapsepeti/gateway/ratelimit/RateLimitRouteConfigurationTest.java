package com.kitapsepeti.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
		"app.jwt.enabled=false"
})
class RateLimitRouteConfigurationTest {

	@Autowired
	private RouteLocator routeLocator;

	@Autowired
	private Environment environment;

	@Test
	void rateLimitedRouteIdsAreRegistered() {
		Set<String> ids = this.routeLocator.getRoutes()
				.map(Route::getId)
				.collect(Collectors.toSet())
				.block();

		assertThat(ids).contains(
				"auth",
				"user-me",
				"catalog-public",
				"catalog-write",
				"catalog-admin",
				"cart",
				"orders-checkout",
				"orders",
				"search",
				"notification",
				"payment-webhook");
	}

	@Test
	void rateLimitEnvDefaultsAreVisible() {
		assertThat(this.environment.getProperty("AUTH_RATE_REPLENISH")).isNotBlank();
		assertThat(this.environment.getProperty("CATALOG_READ_RATE_BURST")).isNotBlank();
		assertThat(this.environment.getProperty("CHECKOUT_RATE_BURST")).isNotBlank();
		assertThat(this.environment.getProperty("WEBHOOK_RATE_REPLENISH")).isNotBlank();
	}

}
