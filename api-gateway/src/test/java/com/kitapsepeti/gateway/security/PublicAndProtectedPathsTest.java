package com.kitapsepeti.gateway.security;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
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
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicAndProtectedPathsTest {

	private static WireMockServer wireMock;
	private static RSAKey rsaKey;
	private static String jwksJson;

	@LocalServerPort
	private int port;

	private WebTestClient webTestClient;

	@BeforeAll
	static void startWireMock() throws Exception {
		wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
		wireMock.start();

		rsaKey = new RSAKeyGenerator(2048).keyID("auth-key-id").generate();
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
		registry.add("USER_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("CATALOG_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("CART_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("ORDER_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
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
	void publicGetBooksWithoutToken_shouldReturn200() {
		wireMock.stubFor(get(urlEqualTo("/api/books"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("[{\"id\":1}]")));

		this.webTestClient.get()
				.uri("/api/books")
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$[0].id")
				.isEqualTo(1);
	}

	@Test
	void publicAuthLoginWithoutToken_shouldReturn200() {
		wireMock.stubFor(post(urlEqualTo("/api/auth/login"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"token\":\"fake-token\"}")));

		this.webTestClient.post()
				.uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"email\":\"test@test.com\",\"password\":\"123456\"}")
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$.token")
				.isEqualTo("fake-token");
	}

	@Test
	void protectedCartWithoutToken_shouldReturn401ProblemDetail() {
		this.webTestClient.get()
				.uri("/api/cart")
				.exchange()
				.expectStatus()
				.isUnauthorized()
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(401)
				.jsonPath("$.code")
				.isEqualTo("AUTHENTICATION_REQUIRED");
	}

	@Test
	void protectedOrdersCheckoutWithoutToken_shouldReturn401ProblemDetail() {
		this.webTestClient.post()
				.uri("/api/orders/checkout")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus()
				.isUnauthorized()
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(401)
				.jsonPath("$.code")
				.isEqualTo("AUTHENTICATION_REQUIRED");
	}

	@Test
	void protectedCartWithCustomerToken_shouldReturn200() throws Exception {
		wireMock.stubFor(get(urlEqualTo("/api/cart"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"items\":[]}")));

		String token = createJwt("customer-1", "ROLE_CUSTOMER");

		this.webTestClient.get()
				.uri("/api/cart")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$.items")
				.isArray();
	}

	@Test
	void adminEndpointWithCustomerToken_shouldReturn403Forbidden() throws Exception {
		String customerToken = createJwt("customer-1", "ROLE_CUSTOMER");

		this.webTestClient.get()
				.uri("/api/admin/metrics")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
				.exchange()
				.expectStatus()
				.isForbidden()
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(403)
				.jsonPath("$.code")
				.isEqualTo("FORBIDDEN");
	}

	@Test
	void adminPostBooksWithCustomerToken_shouldReturn403Forbidden() throws Exception {
		String customerToken = createJwt("customer-1", "ROLE_CUSTOMER");

		this.webTestClient.post()
				.uri("/api/books")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"title\":\"New Book\"}")
				.exchange()
				.expectStatus()
				.isForbidden()
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(403)
				.jsonPath("$.code")
				.isEqualTo("FORBIDDEN");
	}

	@Test
	void adminEndpointWithAdminToken_shouldReturn200() throws Exception {
		wireMock.stubFor(get(urlEqualTo("/api/admin/metrics"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"cpu\":25}")));

		String adminToken = createJwt("admin-1", "ROLE_ADMIN");

		this.webTestClient.get()
				.uri("/api/admin/metrics")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$.cpu")
				.isEqualTo(25);
	}

	@Test
	void adminPostBooksWithAdminToken_shouldReturn200() throws Exception {
		wireMock.stubFor(post(urlEqualTo("/api/books"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"id\":99,\"title\":\"Admin Book\"}")));

		String adminToken = createJwt("admin-1", "ROLE_ADMIN", Instant.now().plusSeconds(3600));

		this.webTestClient.post()
				.uri("/api/books")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"title\":\"Admin Book\"}")
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$.id")
				.isEqualTo(99);
	}

	@Test
	void protectedMeWithValidToken_shouldForwardUserHeadersToDownstream() throws Exception {
		wireMock.stubFor(get(urlEqualTo("/api/me"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"email\":\"user@test.com\"}")));

		String token = createJwt("user-42", "ROLE_CUSTOMER", Instant.now().plusSeconds(3600));

		this.webTestClient.get()
				.uri("/api/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.exchange()
				.expectStatus()
				.isOk()
				.expectHeader()
				.exists("X-Request-Id");

		List<LoggedRequest> requests = wireMock.findAll(getRequestedFor(urlEqualTo("/api/me")));
		assertThat(requests).hasSize(1);
		assertThat(requests.get(0).getHeader("X-User-Id")).isEqualTo("user-42");
		assertThat(requests.get(0).getHeader("X-User-Role")).isEqualTo("ROLE_CUSTOMER");
	}

	@Test
	void publicAuthRefreshWithExpiredAccessToken_shouldStillReachDownstream() throws Exception {
		wireMock.stubFor(post(urlEqualTo("/api/auth/refresh"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"accessToken\":\"new-token\"}")));

		String expiredAccessToken = createJwt("user-42", "ROLE_CUSTOMER", Instant.now().minusSeconds(60));

		this.webTestClient.post()
				.uri("/api/auth/refresh")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"refreshToken\":\"refresh-xyz\"}")
				.exchange()
				.expectStatus()
				.isOk()
				.expectHeader()
				.exists("X-Request-Id")
				.expectBody()
				.jsonPath("$.accessToken")
				.isEqualTo("new-token");

		List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlEqualTo("/api/auth/refresh")));
		assertThat(requests).hasSize(1);
	}

	@Test
	void earlyAuthErrors_shouldIncludeRequestIdHeader() throws Exception {
		this.webTestClient.get()
				.uri("/api/cart")
				.exchange()
				.expectStatus()
				.isUnauthorized()
				.expectHeader()
				.exists("X-Request-Id");

		String customerToken = createJwt("customer-1", "ROLE_CUSTOMER", Instant.now().plusSeconds(3600));
		this.webTestClient.post()
				.uri("/api/books")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"title\":\"x\"}")
				.exchange()
				.expectStatus()
				.isForbidden()
				.expectHeader()
				.exists("X-Request-Id");

		this.webTestClient.get()
				.uri("/internal/stock/check")
				.exchange()
				.expectStatus()
				.isNotFound()
				.expectHeader()
				.exists("X-Request-Id");
	}

	private String createJwt(String subject, String role) throws Exception {
		return createJwt(subject, role, Instant.now().plusSeconds(3600));
	}

	private String createJwt(String subject, String role, Instant expiresAt) throws Exception {
		JWSSigner signer = new RSASSASigner(rsaKey);
		JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
				.subject(subject)
				.claim("role", role)
				.issuer("kitapsepeti-user-service")
				.issueTime(Date.from(Instant.now()))
				.expirationTime(Date.from(expiresAt))
				.build();

		SignedJWT signedJWT = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
				claimsSet
		);
		signedJWT.sign(signer);
		return signedJWT.serialize();
	}

}
