package com.kitapsepeti.user.security;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/**
 * JWT ayarları ({@code app.jwt.*}). Eksik veya hatalı değerde uygulama açılışta durur.
 *
 * @param issuer             token'daki {@code iss} değeri
 * @param accessTtl          access token geçerlilik süresi
 * @param refreshTtl         refresh token geçerlilik süresi
 * @param privateKeyLocation token imzalamak için RSA özel anahtar (PKCS#8 PEM)
 * @param publicKeyLocation  imza doğrulama ve JWKS için RSA açık anahtar (PEM)
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		@NotBlank String issuer,
		@NotNull Duration accessTtl,
		@NotNull Duration refreshTtl,
		@NotNull Resource privateKeyLocation,
		@NotNull Resource publicKeyLocation) {
}
