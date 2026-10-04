package com.kitapsepeti.common.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

@ExtendWith(OutputCaptureExtension.class)
class CircuitBreakersTest {

	private static final CircuitBreakerProperties PROPERTIES = new CircuitBreakerProperties(20, 10, 50,
			Duration.ofSeconds(10), 3);

	private final SettableClock clock = new SettableClock(Instant.parse("2026-03-01T10:00:00Z"));

	@Test
	void configMapsPropertiesToCountBasedWindowWithManualHalfOpen() {
		CircuitBreakerConfig config = CircuitBreakers.config(PROPERTIES, clock);

		assertThat(config.getSlidingWindowType()).isEqualTo(SlidingWindowType.COUNT_BASED);
		assertThat(config.getSlidingWindowSize()).isEqualTo(20);
		assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
		assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
		assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
		assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isFalse();
		assertThat(config.isWritableStackTraceEnabled()).isFalse();
		assertThat(config.getClock()).isSameAs(clock);
	}

	@Test
	void opensAtThresholdAndWaitsOnTheGivenClock() {
		CircuitBreaker breaker = CircuitBreakers.create("catalog", PROPERTIES, clock);
		for (int i = 0; i < 5; i++) {
			success(breaker);
		}
		for (int i = 0; i < 4; i++) {
			failure(breaker);
		}
		assertThat(breaker.getState()).isEqualTo(State.CLOSED);
		failure(breaker);
		assertThat(breaker.getState()).isEqualTo(State.OPEN);

		clock.advance(Duration.ofSeconds(9));
		assertThat(breaker.tryAcquirePermission()).isFalse();
		clock.advance(Duration.ofSeconds(1).plusMillis(1));
		assertThat(breaker.getState()).isEqualTo(State.OPEN);
		assertThat(breaker.tryAcquirePermission()).isTrue();
		assertThat(breaker.getState()).isEqualTo(State.HALF_OPEN);
	}

	@Test
	void stateTransitionsAreLoggedAsWarnWithNameOnly(CapturedOutput output) {
		CircuitBreaker breaker = CircuitBreakers.create("payment", PROPERTIES, clock);
		for (int i = 0; i < 10; i++) {
			failure(breaker);
		}
		clock.advance(Duration.ofSeconds(11));
		for (int i = 0; i < 3; i++) {
			success(breaker);
		}

		assertThat(breaker.getState()).isEqualTo(State.CLOSED);
		assertThat(output.getOut().lines().filter(line -> line.contains("Circuit breaker ")))
			.satisfiesExactly(line -> assertThat(line).contains("WARN").endsWith("Circuit breaker payment CLOSED -> OPEN"),
					line -> assertThat(line).contains("WARN").endsWith("Circuit breaker payment OPEN -> HALF_OPEN"),
					line -> assertThat(line).contains("WARN").endsWith("Circuit breaker payment HALF_OPEN -> CLOSED"));
		assertThat(output).doesNotContain("secret-id-4711");
	}

	@Test
	void eachCreateIsAnIndependentInstance() {
		CircuitBreakerConfig config = CircuitBreakers.config(PROPERTIES, clock);
		CircuitBreaker cart = CircuitBreakers.create("cart", config);
		CircuitBreaker catalog = CircuitBreakers.create("catalog", config);
		for (int i = 0; i < 10; i++) {
			failure(catalog);
		}

		assertThat(catalog.getState()).isEqualTo(State.OPEN);
		assertThat(cart.getState()).isEqualTo(State.CLOSED);
		assertThat(cart.getName()).isEqualTo("cart");
	}

	@Test
	void propertiesBindFromAppCircuitBreakerAndAreValidated() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

		runner.withPropertyValues("app.circuit-breaker.sliding-window-size=20", "app.circuit-breaker.minimum-calls=10",
				"app.circuit-breaker.failure-rate-threshold=50", "app.circuit-breaker.open-duration=10s",
				"app.circuit-breaker.half-open-calls=3")
			.run(context -> assertThat(context.getBean(CircuitBreakerProperties.class)).isEqualTo(PROPERTIES));
		runner.withPropertyValues("app.circuit-breaker.sliding-window-size=20", "app.circuit-breaker.minimum-calls=10",
				"app.circuit-breaker.failure-rate-threshold=101", "app.circuit-breaker.open-duration=10s",
				"app.circuit-breaker.half-open-calls=3")
			.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("app.circuit-breaker.sliding-window-size=20", "app.circuit-breaker.minimum-calls=10",
				"app.circuit-breaker.failure-rate-threshold=50", "app.circuit-breaker.half-open-calls=3")
			.run(context -> assertThat(context).hasFailed());
	}

	private static void success(CircuitBreaker breaker) {
		assertThat(breaker.tryAcquirePermission()).isTrue();
		breaker.onSuccess(1, TimeUnit.MILLISECONDS);
	}

	private static void failure(CircuitBreaker breaker) {
		assertThat(breaker.tryAcquirePermission()).isTrue();
		breaker.onError(1, TimeUnit.MILLISECONDS, new IOException("secret-id-4711"));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(CircuitBreakerProperties.class)
	static class PropertiesConfig {

	}

	private static final class SettableClock extends Clock {

		private volatile Instant now;

		SettableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			this.now = this.now.plus(duration);
		}

		@Override
		public Instant instant() {
			return this.now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			throw new UnsupportedOperationException();
		}

	}

}
