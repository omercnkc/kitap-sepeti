package com.kitapsepeti.cart.support;

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
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * user-service'in JwtService'iyle aynı biçimde (RS256, RFC 7638 thumbprint kid; iss/sub/role/iat/exp/jti) token imzalar.
 * Anahtar çifti test JVM'i açılırken üretilir, diske yazılmaz; açık kısmı {@link JwksServer} sunar.
 * Yalnızca testlerde; cart'ın src/main'inde imzalama kodu yok.
 */
public final class TestJwt {

	public static final String ISSUER = "kitapsepeti-user-service";

	private static final RSAKey KEY = generateKey(null);

	private static final JwtEncoder ENCODER = encoderFor(KEY);

	private TestJwt() {
	}

	/** user-service'in {@code /.well-known/jwks.json} yanıtıyla aynı biçim: yalnızca açık anahtar. */
	public static String publicJwksJson() {
		return new JWKSet(KEY).toPublicJWKSet().toString();
	}

	public static String user(String subject) {
		return token(subject, "USER", ISSUER, now(), now().plus(Duration.ofMinutes(15)), Map.of());
	}

	public static String admin(String subject) {
		return token(subject, "ADMIN", ISSUER, now(), now().plus(Duration.ofMinutes(15)), Map.of());
	}

	public static String expiredUser(String subject) {
		return token(subject, "USER", ISSUER, now().minus(Duration.ofHours(2)), now().minus(Duration.ofHours(1)), Map.of());
	}

	public static String userWithIssuer(String subject, String issuer) {
		return token(subject, "USER", issuer, now(), now().plus(Duration.ofMinutes(15)), Map.of());
	}

	public static String userWithClaims(String subject, Map<String, Object> extraClaims) {
		return token(subject, "USER", ISSUER, now(), now().plus(Duration.ofMinutes(15)), extraClaims);
	}

	/** Başka bir anahtarla imzalı ama bizim kid'imizi taşıyan token; imza doğrulamasında düşmeli. */
	public static String userSignedWithForeignKey(String subject) {
		return sign(encoderFor(generateKey(KEY.getKeyID())), subject, "USER", ISSUER, now(),
				now().plus(Duration.ofMinutes(15)), Map.of());
	}

	/** {@code alg: none} ile imzasız token (RFC 7519 §6.1): {@code header.payload.} */
	public static String unsignedUser(String subject) {
		Instant now = now();
		String header = "{\"alg\":\"none\"}";
		String payload = """
				{"iss":"%s","sub":"%s","role":"USER","iat":%d,"exp":%d,"jti":"%s"}"""
			.formatted(ISSUER, subject, now.getEpochSecond(), now.plus(Duration.ofMinutes(15)).getEpochSecond(),
					UUID.randomUUID());
		return base64Url(header) + "." + base64Url(payload) + ".";
	}

	private static String token(String subject, String role, String issuer, Instant issuedAt, Instant expiresAt,
			Map<String, Object> extraClaims) {
		return sign(ENCODER, subject, role, issuer, issuedAt, expiresAt, extraClaims);
	}

	private static String sign(JwtEncoder encoder, String subject, String role, String issuer, Instant issuedAt,
			Instant expiresAt, Map<String, Object> extraClaims) {
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer(issuer)
			.subject(subject)
			.claim("role", role)
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.id(UUID.randomUUID().toString());
		extraClaims.forEach(claims::claim);
		return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	private static JwtEncoder encoderFor(RSAKey key) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
	}

	/** {@code keyId} null ise kid açık anahtarın thumbprint'i (user-service ile aynı). */
	private static RSAKey generateKey(String keyId) {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			KeyPair keyPair = generator.generateKeyPair();
			RSAKey.Builder builder = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
				.privateKey((RSAPrivateKey) keyPair.getPrivate());
			return (keyId != null) ? builder.keyID(keyId).build() : builder.keyIDFromThumbprint().build();
		}
		catch (NoSuchAlgorithmException | JOSEException ex) {
			throw new IllegalStateException("Test RSA key could not be generated", ex);
		}
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.SECONDS);
	}

	private static String base64Url(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
