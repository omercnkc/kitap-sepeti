package com.kitapsepeti.common.outbox;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Outbox yayıncısı ayarları ({@code app.outbox.*}). Eksik veya hatalı değerde uygulama açılışta durur.
 *
 * @param enabled        false ise worker ({@link OutboxRelay}) hiç oluşturulmaz; satırlar birikir
 * @param exchange       olayların yayınlandığı topic exchange
 * @param pollInterval   bir tur bittikten sonra sonrakine kadar beklenen süre
 * @param batchSize      bir turda kilitlenip yayınlanan en fazla satır
 * @param confirmTimeout her mesaj için broker onayını bekleme süresi
 */
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
		boolean enabled,
		@NotBlank String exchange,
		@NotNull Duration pollInterval,
		@Positive int batchSize,
		@NotNull Duration confirmTimeout) {
}
