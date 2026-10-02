package com.kitapsepeti.cart.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** user-service'in token'a yazdığı {@code iss}; farklı issuer'lı token reddedilir. */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String issuer) {
}
