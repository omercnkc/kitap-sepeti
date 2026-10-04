package com.kitapsepeti.common.resilience;

import java.time.Clock;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resilience4j circuit breaker'larını servisler arasında aynı kuralla kurar (programatik; Spring Cloud
 * CircuitBreakerFactory ve Feign entegrasyonu kullanılmaz). Neyin hata sayılacağına çağıran karar verir
 * ({@code tryAcquirePermission} / {@code onSuccess} / {@code onError}); bu sınıf yalnızca ayarı ve durum logunu verir.
 * <ul>
 * <li>Açık kalma süresi uygulamanın {@link Clock}'uyla ölçülür; açıktan yarı açığa geçiş otomatik değil, süre
 * dolduktan sonraki ilk izin isteğinde olur.</li>
 * <li>Durum değişimleri WARN, yalnızca instance adı ve durumlar (id/tutar yok).</li>
 * <li>Health/readiness'a girmez: bağımlı servis kapalıyken çağıran servis hazır kalır.</li>
 * </ul>
 */
public final class CircuitBreakers {

	private static final Logger log = LoggerFactory.getLogger(CircuitBreakers.class);

	private CircuitBreakers() {
	}

	public static CircuitBreakerConfig config(CircuitBreakerProperties properties, Clock clock) {
		return CircuitBreakerConfig.custom()
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
	}

	public static CircuitBreaker create(String name, CircuitBreakerConfig config) {
		CircuitBreaker breaker = CircuitBreaker.of(name, config);
		breaker.getEventPublisher()
			.onStateTransition(event -> log.warn("Circuit breaker {} {} -> {}", event.getCircuitBreakerName(),
					event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
		return breaker;
	}

	public static CircuitBreaker create(String name, CircuitBreakerProperties properties, Clock clock) {
		return create(name, config(properties, clock));
	}

}
