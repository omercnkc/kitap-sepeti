package com.kitapsepeti.payment.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Order'ın ödeme oluşturma isteği. Aynı {@code orderId} ile tekrar gönderilebilir (idempotent); tekrar istekte
 * kullanıcı, tutar ve para birimi ilk istekle aynı olmalı.
 *
 * @param amount pozitif, en fazla 10 tam + 2 ondalık basamak ({@code DECIMAL(12,2)}); 10.001 reddedilir, yuvarlanmaz
 * @param currency ISO 4217, büyük harf (ör. {@code TRY})
 */
public record CreatePaymentRequest(
		@NotNull UUID orderId,
		@NotNull UUID userId,
		@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
		@NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency) {
}
