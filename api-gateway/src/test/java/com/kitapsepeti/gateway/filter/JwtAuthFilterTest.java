package com.kitapsepeti.gateway.filter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
		"app.jwt.protected-paths=/api/orders/**"
})
class JwtAuthFilterTest {

	private static WireMockServer wireMock;
	private static RSAKey rsaKey;
	private static RSAKey differentRsaKey;
	private static String jwksJson;

	@LocalServerPort
	private int port;

	private WebTestClient webTestClient;

	@BeforeAll
	static void initKeysAndWireMock() throws Exception {
		wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
		wireMock.start();

		rsaKey = new RSAKeyGenerator(2048).keyID("test-key-id").generate();
		differentRsaKey = new RSAKeyGenerator(2048).keyID("different-key-id").generate();

		JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());
		jwksJson = jwkSet.toString();
	}

	@AfterAll
	static void stopWireMock() {
		if (wireMock != null) {
			wireMock.stop();
		}
	}

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("ORDER_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("CATALOG_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("JWKS_URL", () -> "http://localhost:" + wireMock.port() + "/.well-known/jwks.json");
	}

	@BeforeEach
	void setUp() {
		this.webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + this.port).build();
		wireMock.resetAll();

		wireMock.stubFor(get(urlEqualTo("/.well-known/jwks.json"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody(jwksJson)));
	}

	@Test
	void validToken_shouldForwardHeadersToDownstream() throws Exception {
		wireMock.stubFor(get(urlEqualTo("/api/orders/order-101"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"orderId\":\"order-101\"}")));

		String token = createJwt(rsaKey, "user-123", "ROLE_CUSTOMER", Instant.now().plusSeconds(3600));

		this.webTestClient.get()
				.uri("/api/orders/order-101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$.orderId")
				.isEqualTo("order-101");

		List<LoggedRequest> requests = wireMock.findAll(getRequestedFor(urlEqualTo("/api/orders/order-101")));
		assertThat(requests).hasSize(1);
		LoggedRequest downstreamRequest = requests.get(0);
		assertThat(downstreamRequest.getHeader("X-User-Id")).isEqualTo("user-123");
		assertThat(downstreamRequest.getHeader("X-User-Role")).isEqualTo("ROLE_CUSTOMER");
		assertThat(downstreamRequest.getHeader("X-Request-Id")).isNotBlank();
	}

	@Test
	void missingTokenOnProtectedEndpoint_shouldReturn401ProblemDetail() {
		this.webTestClient.get()
				.uri("/api/orders/order-101")
				.exchange()
				.expectStatus()
				.isUnauthorized()
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(401)
				.jsonPath("$.code")
				.isEqualTo("AUTHENTICATION_REQUIRED")
				.jsonPath("$.title")
				.isEqualTo("Kimlik doğrulaması gerekli");
	}

	@Test
	void expiredToken_shouldReturn401ProblemDetail() throws Exception {
		String expiredToken = createJwt(rsaKey, "user-123", "ROLE_CUSTOMER", Instant.now().minusSeconds(3600));

		this.webTestClient.get()
				.uri("/api/orders/order-101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
				.exchange()
				.expectStatus()
				.isUnauthorized()
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(401)
				.jsonPath("$.code")
				.isEqualTo("TOKEN_EXPIRED")
				.jsonPath("$.title")
				.isEqualTo("Belirtecin süresi dolmuş");
	}

	@Test
	void invalidSignatureToken_shouldReturn401ProblemDetail() throws Exception {
		String untrustedToken = createJwt(differentRsaKey, "user-123", "ROLE_CUSTOMER", Instant.now().plusSeconds(3600));

		this.webTestClient.get()
				.uri("/api/orders/order-101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + untrustedToken)
				.exchange()
				.expectStatus()
				.isUnauthorized()
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(401)
				.jsonPath("$.code")
				.isEqualTo("INVALID_TOKEN");
	}

	@Test
	void jwksCache_shouldNotRefetchJwksOnSubsequentRequests() throws Exception {
		wireMock.stubFor(get(urlEqualTo("/api/orders/order-201"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"orderId\":\"order-201\"}")));

		String token1 = createJwt(rsaKey, "user-1", "ROLE_CUSTOMER", Instant.now().plusSeconds(3600));
		String token2 = createJwt(rsaKey, "user-2", "ROLE_CUSTOMER", Instant.now().plusSeconds(3600));

		this.webTestClient.get()
				.uri("/api/orders/order-201")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
				.exchange()
				.expectStatus()
				.isOk();

		this.webTestClient.get()
				.uri("/api/orders/order-201")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token2)
				.exchange()
				.expectStatus()
				.isOk();

		wireMock.verify(exactly(1), getRequestedFor(urlEqualTo("/.well-known/jwks.json")));
	}

	@Test
	void unprotectedPathWithoutToken_shouldPassThrough() {
		wireMock.stubFor(get(urlEqualTo("/api/books/free-book"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"title\":\"Public Book\"}")));

		this.webTestClient.get()
				.uri("/api/books/free-book")
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$.title")
				.isEqualTo("Public Book");
	}

	private String createJwt(RSAKey key, String subject, String role, Instant expiresAt) throws Exception {
		JWSSigner signer = new RSASSASigner(key);
		JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
				.subject(subject)
				.claim("role", role)
				.issuer("kitapsepeti-user-service")
				.issueTime(Date.from(Instant.now()))
				.expirationTime(Date.from(expiresAt))
				.build();

		SignedJWT signedJWT = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
				claimsSet
		);
		signedJWT.sign(signer);
		return signedJWT.serialize();
	}

}
