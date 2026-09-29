package com.kitapsepeti.user.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.kitapsepeti.user.entity.User;
import com.kitapsepeti.user.security.JwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * RS256 imzalı access token üretir. Token'a yalnızca yetkilendirme için gereken
 * bilgiler konur (kullanıcı id'si ve rol); e-posta, parola hash'i, telefon gibi kişisel veri KONMAZ.
 */
@Service
public class JwtService {

	private final JwtEncoder jwtEncoder;

	private final JwtProperties properties;

	private final Clock clock;

	private final String keyId;

	public JwtService(JwtEncoder jwtEncoder, JWKSet jwkSet, JwtProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
		this.keyId = jwkSet.getKeys().getFirst().getKeyID();
	}

	public AccessToken issueAccessToken(User user) {
		// JWT zamanları saniye hassasiyetinde; kesmezsek dönen expiresAt token'daki exp'ten farklı olur.
		Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		Instant expiresAt = issuedAt.plus(properties.accessTtl());

		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
			.keyId(keyId)
			.build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(user.getId().toString())
			.claim("role", user.getRole().name())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.id(UUID.randomUUID().toString())
			.build();

		String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(value, expiresAt);
	}

}
