package com.kitapsepeti.order.client;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Servis başına bağımsız circuit breaker (cart, catalog, payment), Resilience4j API'siyle programatik. Feign'in kendi
 * circuit breaker entegrasyonu ve Spring Cloud CircuitBreakerFactory kullanılmaz: neyin hata sayılacağına
 * {@link RemoteCalls} karar verir (yalnızca bağlantı, zaman aşımı, 5xx ve sözleşmeye uymayan 2xx). Durum değişimleri
 * WARN (instance adıyla). Circuit breaker durumu health/readiness'a girmez: bağımlı servis kapalıyken Order hazır kalır.
 * Açık kalma süresi uygulamanın {@link Clock}'uyla ölçülür.
 */
@Component
public class DownstreamCircuitBreakers {

	private static final Logger log = LoggerFactory.getLogger(DownstreamCircuitBreakers.class);

	private final Map<Downstream, CircuitBreaker> breakers = new EnumMap<>(Downstream.class);

	public DownstreamCircuitBreakers(CircuitBreakerProperties properties, Clock clock) {
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
			.slidingWindowType(SlidingWindowType.COUNT_BASED)
			.slidingWindowSize(properties.slidingWindowSize())
			.minimumNumberOfCalls(properties.minimumCalls())
			.failureRateThreshold(properties.failureRateThreshold())
			.waitDurationInOpenState(properties.openDuration())
			.permittedNumberOfCallsInHalfOpenState(properties.halfOpenCalls())
			.automaticTransitionFromOpenToHalfOpenEnabled(false)
			.writableStackTraceEnabled(false)
			.clock(clock)
			.build();
		for (Downstream downstream : Downstream.values()) {
			CircuitBreaker breaker = CircuitBreaker.of(downstream.id(), config);
			breaker.getEventPublisher()
				.onStateTransition(event -> log.warn("Circuit breaker {} {} -> {}", event.getCircuitBreakerName(),
						event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
			this.breakers.put(downstream, breaker);
		}
	}

	public CircuitBreaker get(Downstream downstream) {
		return this.breakers.get(downstream);
	}

}
