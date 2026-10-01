package com.kitapsepeti.catalog.service;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Stok ayarları ({@code app.stock.*}).
 *
 * @param reservationTtl 'held' rezervasyonun geçerlilik süresi; {@code expiresAt = şimdi + reservationTtl}
 */
@Validated
@ConfigurationProperties(prefix = "app.stock")
public record StockProperties(@NotNull Duration reservationTtl) {
}
