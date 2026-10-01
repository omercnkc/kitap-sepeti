package com.kitapsepeti.catalog.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT doğrulama ayarları ({@code app.jwt.*}). Eksik değerde uygulama açılışta durur.
 *
 * @param issuer doğrulanan token'larda beklenen {@code iss}; user-service'in {@code app.jwt.issuer}'ı ile aynı.
 *               URI değil düz metin olduğu için Boot'un {@code issuer-uri} özelliği kullanılmaz.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String issuer) {
}
