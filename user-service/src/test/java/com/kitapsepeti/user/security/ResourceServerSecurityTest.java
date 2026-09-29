package com.kitapsepeti.user.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.kitapsepeti.user.ApiTestSupport;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

class ResourceServerSecurityTest extends ApiTestSupport {

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JWKSet jwkSet;

	@Autowired
	private JwtProperties jwtProperties;

	@Autowired
	private Clock clock;

	@Test
	void missingTokenReturns401WithBearerChallenge() throws Exception {
		mockMvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.instance").value("/api/me"));
	}

	@Test
	void tamperedSignatureIsRejected() throws Exception {
		String token = registerAndGetAccessToken("imza@kitapsepeti.com");
		int signatureStart = token.lastIndexOf('.') + 1;
		char first = token.charAt(signatureStart);
		String tampered = token.substring(0, signatureStart) + (first == 'A' ? 'B' : 'A')
				+ token.substring(signatureStart + 1);

		assertInvalidToken(tampered);
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		registerAndGetAccessToken("sure@kitapsepeti.com");
		String userId = userIdOf("sure@kitapsepeti.com");
		Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);

		assertInvalidToken(sign(jwtEncoder, userId, now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)),
				jwtProperties.issuer()));
	}

	@Test
	void tokenSignedWithForeignKeyIsRejectedEvenWithOurKid() throws Exception {
		registerAndGetAccessToken("yabanci@kitapsepeti.com");
		String userId = userIdOf("yabanci@kitapsepeti.com");
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair keyPair = generator.generateKeyPair();
		RSAKey foreignKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
			.privateKey((RSAPrivateKey) keyPair.getPrivate())
			.keyID(jwkSet.getKeys().getFirst().getKeyID())
			.build();
		JwtEncoder foreignEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(foreignKey)));
		Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);

		assertInvalidToken(sign(foreignEncoder, userId, now, now.plus(Duration.ofMinutes(15)), jwtProperties.issuer()));
	}

	@Test
	void tokenWithWrongIssuerOrGarbageIsRejected() throws Exception {
		registerAndGetAccessToken("issuer@kitapsepeti.com");
		String userId = userIdOf("issuer@kitapsepeti.com");
		Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);

		assertInvalidToken(sign(jwtEncoder, userId, now, now.plus(Duration.ofMinutes(15)), "baska-servis"));
		assertInvalidToken("bu-bir-jwt-degil");
	}

	@Test
	void adminRoleClaimBecomesRoleAdminAuthority() throws Exception {
		String userToken = registerAndGetAccessToken("kullanici@kitapsepeti.com");
		registerAndGetAccessToken("yonetici@kitapsepeti.com");
		jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", "yonetici@kitapsepeti.com");
		String adminToken = loginAndGetAccessToken("yonetici@kitapsepeti.com");

		mockMvc.perform(get("/test/exceptions/admin").with(bearer(adminToken)))
			.andExpect(status().isOk());
		mockMvc.perform(get("/test/exceptions/admin").with(bearer(userToken)))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void authEndpointsIgnoreStaleAuthorizationHeader() throws Exception {
		registerAndGetAccessToken("eski-baslik@kitapsepeti.com");

		mockMvc.perform(post("/api/auth/login")
				.with(bearer("suresi-dolmus-veya-bozuk-token"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"eski-baslik@kitapsepeti.com","password":"%s"}
						""".formatted(PASSWORD)))
			.andExpect(status().isOk());
	}

	private void assertInvalidToken(String token) throws Exception {
		ResultActions result = mockMvc.perform(get("/api/me").with(bearer(token)))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.INVALID_TOKEN))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		result.andExpect(jsonPath("$.detail").value("Authentication is required."));
	}

	private static String sign(JwtEncoder encoder, String subject, Instant issuedAt, Instant expiresAt,
			String issuer) {
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(issuer)
			.subject(subject)
			.claim("role", "USER")
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.id(UUID.randomUUID().toString())
			.build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
