package com.kitapsepeti.order.client;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

import com.kitapsepeti.common.resilience.CircuitBreakerProperties;
import com.kitapsepeti.common.resilience.CircuitBreakers;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.stereotype.Component;

/**
 * Servis başına bağımsız circuit breaker (cart, catalog, payment), ortak kuralla ({@link CircuitBreakers}). Feign'in
 * kendi circuit breaker entegrasyonu ve Spring Cloud CircuitBreakerFactory kullanılmaz: neyin hata sayılacağına
 * {@link RemoteCalls} karar verir (yalnızca bağlantı, zaman aşımı, 5xx ve sözleşmeye uymayan 2xx).
 */
@Component
public class DownstreamCircuitBreakers {

	private final Map<Downstream, CircuitBreaker> breakers = new EnumMap<>(Downstream.class);

	public DownstreamCircuitBreakers(CircuitBreakerProperties properties, Clock clock) {
		CircuitBreakerConfig config = CircuitBreakers.config(properties, clock);
		for (Downstream downstream : Downstream.values()) {
			this.breakers.put(downstream, CircuitBreakers.create(downstream.id(), config));
		}
	}

	public CircuitBreaker get(Downstream downstream) {
		return this.breakers.get(downstream);
	}

}
