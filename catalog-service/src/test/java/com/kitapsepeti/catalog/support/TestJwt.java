package com.kitapsepeti.catalog.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * user-service'in test anahtar çiftiyle, onun JwtService'iyle aynı biçimde (RS256, RFC 7638 thumbprint kid;
 * iss/sub/role/iat/exp/jti) token imzalar. Yalnızca testlerde; Catalog'un src/main'inde imzalama kodu yok.
 */
public final class TestJwt {

	public static final String ISSUER = "kitapsepeti-user-service";

	private static final RSAKey KEY = loadKey();

	private static final JwtEncoder ENCODER = encoderFor(KEY);

	private TestJwt() {
	}

	/** user-service'in {@code /.well-known/jwks.json} yanıtıyla aynı biçim: yalnızca açık anahtar. */
	public static String publicJwksJson() {
		return new JWKSet(KEY).toPublicJWKSet().toString();
	}

	public static String user(String subject) {
		return token(subject, "USER", ISSUER, now(), now().plus(Duration.ofMinutes(15)));
	}

	public static String admin(String subject) {
		return token(subject, "ADMIN", ISSUER, now(), now().plus(Duration.ofMinutes(15)));
	}

	public static String expiredAdmin(String subject) {
		return token(subject, "ADMIN", ISSUER, now().minus(Duration.ofHours(2)), now().minus(Duration.ofHours(1)));
	}

	public static String adminWithIssuer(String subject, String issuer) {
		return token(subject, "ADMIN", issuer, now(), now().plus(Duration.ofMinutes(15)));
	}

	public static String token(String subject, String role, String issuer, Instant issuedAt, Instant expiresAt) {
		return sign(ENCODER, subject, role, issuer, issuedAt, expiresAt);
	}

	/** Başka bir anahtarla imzalı ama bizim kid'imizi taşıyan token; imza doğrulamasında düşmeli. */
	public static String adminSignedWithForeignKey(String subject) {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			KeyPair keyPair = generator.generateKeyPair();
			RSAKey foreignKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
				.privateKey((RSAPrivateKey) keyPair.getPrivate())
				.keyID(KEY.getKeyID())
				.build();
			return sign(encoderFor(foreignKey), subject, "ADMIN", ISSUER, now(), now().plus(Duration.ofMinutes(15)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** {@code alg: none} ile imzasız token (RFC 7519 §6.1): {@code header.payload.} */
	public static String unsignedAdmin(String subject) {
		Instant now = now();
		String header = "{\"alg\":\"none\"}";
		String payload = """
				{"iss":"%s","sub":"%s","role":"ADMIN","iat":%d,"exp":%d,"jti":"%s"}"""
			.formatted(ISSUER, subject, now.getEpochSecond(), now.plus(Duration.ofMinutes(15)).getEpochSecond(),
					UUID.randomUUID());
		return base64Url(header) + "." + base64Url(payload) + ".";
	}

	private static String sign(JwtEncoder encoder, String subject, String role, String issuer, Instant issuedAt,
			Instant expiresAt) {
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(issuer)
			.subject(subject)
			.claim("role", role)
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.id(UUID.randomUUID().toString())
			.build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private static JwtEncoder encoderFor(RSAKey key) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
	}

	private static RSAKey loadKey() {
		try (InputStream publicPem = new ClassPathResource("jwt/test-public.pem").getInputStream();
				InputStream privatePem = new ClassPathResource("jwt/test-private.pem").getInputStream()) {
			RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicPem);
			RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privatePem);
			return new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
		}
		catch (IOException | JOSEException ex) {
			throw new IllegalStateException("Test JWT key pair could not be loaded", ex);
		}
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.SECONDS);
	}

	private static String base64Url(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
