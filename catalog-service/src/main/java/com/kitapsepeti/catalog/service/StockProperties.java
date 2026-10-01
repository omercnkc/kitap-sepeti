package com.kitapsepeti.catalog.service;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Stok ayarları ({@code app.stock.*}).
 *
 * @param reservationTtl 'held' rezervasyonun geçerlilik süresi; {@code expiresAt = şimdi + reservationTtl}
 * @param expiry süresi dolan rezervasyonları serbest bırakan görev ({@link ReservationExpiryJob})
 */
@Validated
@ConfigurationProperties(prefix = "app.stock")
public record StockProperties(@NotNull Duration reservationTtl, @NotNull @Valid Expiry expiry) {

	/**
	 * @param enabled false ise görev (ve başka iş yoksa zamanlayıcı) hiç oluşturulmaz
	 * @param interval bir tur bittikten sonra sonrakine kadar beklenen süre
	 * @param batchSize bir turda ele alınan en fazla sipariş
	 */
	public record Expiry(boolean enabled, @NotNull Duration interval, @Positive int batchSize) {
	}

}
