package com.kitapsepeti.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import com.kitapsepeti.user.config.RsaKeyConfig;
import com.kitapsepeti.user.entity.Role;
import com.kitapsepeti.user.entity.User;
import com.kitapsepeti.user.security.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");

	private final RsaKeyConfig config = new RsaKeyConfig();

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private JwtProperties properties;

	private RSAKey rsaKey;

	private JWKSet jwkSet;

	private JwtService jwtService;

	private JwtDecoder jwtDecoder;

	private User user;

	@BeforeEach
	void setUp() {
		properties = new JwtProperties("kitapsepeti-user-service", Duration.ofMinutes(15), Duration.ofDays(14),
				new ClassPathResource("jwt/test-private.pem"), new ClassPathResource("jwt/test-public.pem"));
		rsaKey = config.rsaKey(properties);
		jwkSet = config.jwkSet(rsaKey);
		jwtService = new JwtService(config.jwtEncoder(jwkSet), jwkSet, properties, clock);
		jwtDecoder = config.jwtDecoder(rsaKey, properties, clock);

		user = new User("ayse@kitapsepeti.com", "$2a$10$hash", "Ayşe", "Yılmaz");
		user.setPhone("5550000000");
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
	}

	@Test
	void issuedTokenDecodesWithExpectedClaims() {
		AccessToken token = jwtService.issueAccessToken(user);

		Jwt jwt = jwtDecoder.decode(token.value());

		assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
		assertThat(jwt.getClaimAsString("role")).isEqualTo(Role.USER.name());
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("kitapsepeti-user-service");
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
		assertThat(jwt.getId()).isNotBlank();
		assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());
	}

	@Test
	void headerKidMatchesJwksKid() throws Exception {
		Jwt jwt = jwtDecoder.decode(jwtService.issueAccessToken(user).value());

		String jwksKid = jwkSet.toPublicJWKSet().getKeys().getFirst().getKeyID();
		assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", jwksKid);
		assertThat(jwksKid).isEqualTo(rsaKey.computeThumbprint().toString());
	}

	@Test
	void rejectsTokenWithTamperedPayload() {
		String[] parts = jwtService.issueAccessToken(user).value().split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
		String subject = user.getId().toString();
		char last = subject.charAt(subject.length() - 1);
		String tamperedSubject = subject.substring(0, subject.length() - 1) + (last == '0' ? '1' : '0');
		String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
			.encodeToString(payload.replace(subject, tamperedSubject).getBytes(StandardCharsets.UTF_8));
		String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

		assertThatThrownBy(() -> jwtDecoder.decode(tampered))
			.isInstanceOf(JwtException.class)
			.hasMessageContaining("Invalid signature");
	}

	@Test
	void rejectsTokenSignedWithDifferentKey() throws Exception {
		RSAKey otherKey = new RSAKeyGenerator(2048)
			.keyUse(KeyUse.SIGNATURE)
			.algorithm(JWSAlgorithm.RS256)
			.keyIDFromThumbprint(true)
			.generate();
		JWKSet otherJwkSet = new JWKSet(otherKey);
		JwtService otherService = new JwtService(config.jwtEncoder(otherJwkSet), otherJwkSet, properties, clock);

		String foreignToken = otherService.issueAccessToken(user).value();

		assertThatThrownBy(() -> jwtDecoder.decode(foreignToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsExpiredToken() {
		String token = jwtService.issueAccessToken(user).value();
		Clock afterExpiry = Clock.offset(clock, Duration.ofMinutes(20));
		JwtDecoder laterDecoder = config.jwtDecoder(rsaKey, properties, afterExpiry);

		assertThatThrownBy(() -> laterDecoder.decode(token))
			.isInstanceOf(JwtValidationException.class)
			.hasMessageContaining("expired");
	}

	@Test
	void payloadContainsNoPersonalData() {
		Jwt jwt = jwtDecoder.decode(jwtService.issueAccessToken(user).value());

		assertThat(jwt.getClaims()).doesNotContainKeys("email", "password", "passwordHash", "phone");
		assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "role", "iat", "exp", "jti");
	}

}
