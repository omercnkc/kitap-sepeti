package com.kitapsepeti.payment.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Ödeme oluşturma isteği. Aynı `orderId` ile tekrar gönderilebilir; `userId`, `amount` ve "
		+ "`currency` ilk istekle aynı olmalı.")
public record CreatePaymentRequest(
		@Schema(description = "Siparişin id'si; bir siparişin tek ödemesi vardır.") @NotNull UUID orderId,
		@Schema(description = "Siparişi veren kullanıcının id'si. Yalnızca eşleşme kontrolünde kullanılır; yanıtta "
				+ "yer almaz.") @NotNull UUID userId,
		@Schema(description = "Ödenecek tutar: pozitif, en fazla 10 tam ve 2 ondalık basamak; fazla ondalık "
				+ "reddedilir, yuvarlanmaz.")
		@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
		@Schema(description = "ISO 4217 para birimi, büyük harf.", example = "TRY")
		@NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency) {
}
