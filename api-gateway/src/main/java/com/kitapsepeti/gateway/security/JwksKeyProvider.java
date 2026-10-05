package com.kitapsepeti.gateway.security;

import com.kitapsepeti.gateway.config.JwtProperties;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class JwksKeyProvider {

	private static final Logger log = LoggerFactory.getLogger(JwksKeyProvider.class);

	private final JwtProperties jwtProperties;
	private final WebClient webClient;
	private final AtomicReference<CachedJwks> cachedJwks = new AtomicReference<>();

	public JwksKeyProvider(JwtProperties jwtProperties, Optional<WebClient.Builder> webClientBuilder) {
		this.jwtProperties = jwtProperties;
		this.webClient = webClientBuilder.orElseGet(WebClient::builder).build();
	}

	public Mono<JWK> getKey(String kid) {
		CachedJwks current = this.cachedJwks.get();
		if (current != null && Instant.now().isBefore(current.expiresAt())) {
			JWK found = findKey(current.jwkSet(), kid);
			if (found != null) {
				return Mono.just(found);
			}
		}

		return fetchJwks()
				.map(jwkSet -> {
					JWK found = findKey(jwkSet, kid);
					if (found == null) {
						throw new IllegalArgumentException("No suitable RSA key found in JWKS for kid: " + kid);
					}
					return found;
				});
	}

	private Mono<JWKSet> fetchJwks() {
		return this.webClient.get()
				.uri(this.jwtProperties.getJwksUrl())
				.retrieve()
				.bodyToMono(String.class)
				.timeout(Duration.ofSeconds(5))
				.map(json -> {
					try {
						JWKSet jwkSet = JWKSet.parse(json);
						Instant expiresAt = Instant.now().plusSeconds(this.jwtProperties.getCacheTtlSeconds());
						this.cachedJwks.set(new CachedJwks(jwkSet, expiresAt));
						log.debug("Successfully refreshed and cached JWKS until {}", expiresAt);
						return jwkSet;
					} catch (ParseException e) {
						log.warn("Failed to parse JWKS: {}", e.getMessage());
						throw new IllegalStateException("Failed to parse JWKS response: " + e.getMessage(), e);
					}
				})
				.onErrorResume(ex -> {
					CachedJwks stale = this.cachedJwks.get();
					if (stale != null) {
						log.warn("Failed to fetch fresh JWKS, falling back to stale cache: {}", ex.getMessage());
						return Mono.just(stale.jwkSet());
					}
					return Mono.error(ex);
				});
	}

	private JWK findKey(JWKSet jwkSet, String kid) {
		if (kid != null && !kid.isBlank()) {
			JWK key = jwkSet.getKeyByKeyId(kid);
			if (key != null) {
				return key;
			}
		}
		for (JWK key : jwkSet.getKeys()) {
			if (KeyType.RSA.equals(key.getKeyType())) {
				return key;
			}
		}
		return null;
	}

	private record CachedJwks(JWKSet jwkSet, Instant expiresAt) {}

}
