package com.kitapsepeti.cart.config;

import java.time.Clock;

import com.kitapsepeti.common.resilience.CircuitBreakerProperties;
import com.kitapsepeti.common.resilience.CircuitBreakers;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Catalog çağrılarının circuit breaker'ı ({@code app.circuit-breaker.*}); neyin hata sayılacağına
 * {@link com.kitapsepeti.cart.client.CatalogGateway} karar verir. Health/readiness'a girmez.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class CatalogCircuitBreakerConfig {

	public static final String CATALOG = "catalog";

	@Bean
	CircuitBreaker catalogCircuitBreaker(CircuitBreakerProperties properties, Clock clock) {
		return CircuitBreakers.create(CATALOG, properties, clock);
	}

}
