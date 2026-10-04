package com.kitapsepeti.order.client;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Her bağımlı servis için ayrı circuit breaker'ın ortak ayarı ({@code app.circuit-breaker.*}). Pencere sayı tabanlı.
 *
 * @param slidingWindowSize son kaç çağrıya bakılır
 * @param minimumCalls hata oranı hesaplanmadan önce gereken çağrı sayısı
 * @param failureRateThreshold yüzde; bu orana ulaşınca açılır
 * @param openDuration açık kalma süresi (sonra yarı açık)
 * @param halfOpenCalls yarı açıkta izin verilen deneme çağrısı
 */
@Validated
@ConfigurationProperties(prefix = "app.circuit-breaker")
public record CircuitBreakerProperties(
		@Min(1) int slidingWindowSize,
		@Min(1) int minimumCalls,
		@Min(1) @Max(100) int failureRateThreshold,
		@NotNull Duration openDuration,
		@Min(1) int halfOpenCalls) {
}
