package com.kitapsepeti.gateway.ratelimit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.utility.DockerImageName;

/**
 * Rate limit entegrasyon testleri (Testcontainers Redis).
 * Düşük burst yalnızca bu sınıfın DynamicPropertySource'unda; global
 * {@code src/test/resources/application.properties} (100000) diğer testler için kalır.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RateLimitIT.RedisTestcontainersConfig.class)
class RateLimitIT {

	private static final int AUTH_BURST = 2;
	private static final int CART_BURST = 2;

	private static WireMockServer wireMock;
	private static RSAKey rsaKey;
	private static String jwksJson;

	@LocalServerPort
	private int port;

	private WebTestClient webTestClient;

	@TestConfiguration(proxyBeanMethods = false)
	static class RedisTestcontainersConfig {

		@Bean
		@ServiceConnection
		RedisContainer redisContainer() {
			return new RedisContainer(DockerImageName.parse("redis:7-alpine"));
		}

	}

	@BeforeAll
	static void startWireMockAndKeys() throws Exception {
		wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
		wireMock.start();

		rsaKey = new RSAKeyGenerator(2048).keyID("rate-limit-it-key").generate();
		jwksJson = new JWKSet(rsaKey.toPublicJWK()).toString();
	}

	@AfterAll
	static void stopWireMock() {
		if (wireMock != null) {
			wireMock.stop();
		}
	}

	@DynamicPropertySource
	static void configureRateLimitsAndDownstream(DynamicPropertyRegistry registry) {
		// Bu IT için düşük kota — production / global test defaults değişmez.
		registry.add("AUTH_RATE_REPLENISH", () -> "1");
		registry.add("AUTH_RATE_BURST", () -> String.valueOf(AUTH_BURST));
		registry.add("CART_RATE_REPLENISH", () -> "1");
		registry.add("CART_RATE_BURST", () -> String.valueOf(CART_BURST));

		// Port lazy: @BeforeAll WireMock ayağa kalktıktan sonra çözülsün.
		registry.add("USER_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("CART_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		// JWKS doğrudan WireMock'a (gateway rate-limit'ine takılmasın).
		registry.add("JWKS_URL",
				() -> "http://localhost:" + wireMock.port() + "/.well-known/jwks.json");
		registry.add("app.jwt.jwks-url",
				() -> "http://localhost:" + wireMock.port() + "/.well-known/jwks.json");
	}

	@BeforeEach
	void setUp() {
		this.webTestClient = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + this.port)
				.responseTimeout(Duration.ofSeconds(30))
				.build();
		wireMock.resetAll();

		wireMock.stubFor(get(urlEqualTo("/.well-known/jwks.json"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody(jwksJson)));

		wireMock.stubFor(post(urlEqualTo("/api/auth/login"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"accessToken\":\"ok\"}")));

		wireMock.stubFor(get(urlEqualTo("/api/cart"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"items\":[]}")));
	}

	@Test
	void auth_sameIp_burstPlusOne_returns429ProblemDetails() {
		String ip = "203.0.113.10";

		for (int i = 0; i < AUTH_BURST; i++) {
			EntityExchangeResult<byte[]> allowed = postAuthLogin(ip);
			assertThat(allowed.getStatus().value())
					.as("istek %d 429 olmamalı", i + 1)
					.isNotEqualTo(429);
		}

		this.webTestClient.post()
				.uri("/api/auth/login")
				.header("X-Forwarded-For", ip)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"email\":\"a@b.com\",\"password\":\"x\"}")
				.exchange()
				.expectStatus()
				.isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectHeader()
				.exists("Retry-After")
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(429)
				.jsonPath("$.code")
				.isEqualTo("RATE_LIMIT_EXCEEDED")
				.jsonPath("$.title")
				.isEqualTo("İstek sınırı aşıldı");
	}

	@Test
	void auth_underLimit_isNot429() {
		EntityExchangeResult<byte[]> result = postAuthLogin("198.51.100.20");
		assertThat(result.getStatus().value()).isNotEqualTo(429);
		assertThat(result.getStatus().is2xxSuccessful() || result.getStatus().value() == 401).isTrue();
	}

	@Test
	void auth_differentXff_doNotShareQuota() {
		String ipA = "203.0.113.50";
		String ipB = "203.0.113.51";

		for (int i = 0; i < AUTH_BURST; i++) {
			assertThat(postAuthLogin(ipA).getStatus().value()).isNotEqualTo(429);
		}
		assertThat(postAuthLogin(ipA).getStatus().value()).isEqualTo(429);

		EntityExchangeResult<byte[]> otherIp = postAuthLogin(ipB);
		assertThat(otherIp.getStatus().value())
				.as("farklı X-Forwarded-For kotayı paylaşmamalı")
				.isNotEqualTo(429);
	}

	@Test
	void cart_sameUser_burstPlusOne_returns429() throws Exception {
		String token = createJwt(rsaKey, "user-rl-same-" + System.nanoTime(), "ROLE_CUSTOMER",
				Instant.now().plusSeconds(3600));

		EntityExchangeResult<byte[]> denied = exhaustCartQuota(token);
		assertThat(denied.getStatus().value()).isEqualTo(429);
		MediaType contentType = denied.getResponseHeaders().getContentType();
		assertThat(contentType).isNotNull();
		assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
		assertThat(new String(denied.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8))
				.contains("RATE_LIMIT_EXCEEDED");
	}

	@Test
	void cart_differentUsers_doNotShareQuota() throws Exception {
		long n = System.nanoTime();
		String tokenA = createJwt(rsaKey, "user-rl-a-" + n, "ROLE_CUSTOMER", Instant.now().plusSeconds(3600));
		String tokenB = createJwt(rsaKey, "user-rl-b-" + n, "ROLE_CUSTOMER", Instant.now().plusSeconds(3600));

		assertThat(exhaustCartQuota(tokenA).getStatus().value()).isEqualTo(429);

		EntityExchangeResult<byte[]> userB = getCart(tokenB);
		assertThat(userB.getStatus().value())
				.as("farklı JWT sub ayrı sayaç kullanmalı (status=%s)", userB.getStatus())
				.isEqualTo(200);
	}

	/**
	 * Burst kadar 200 bekler; ardından kısa pencerede 429 arar (FAIL-OPEN / bucket yenileme flake’ine karşı).
	 */
	private EntityExchangeResult<byte[]> exhaustCartQuota(String token) {
		for (int i = 0; i < CART_BURST; i++) {
			EntityExchangeResult<byte[]> allowed = getCart(token);
			assertThat(allowed.getStatus().value())
					.as("cart istek %d JWT+limit OK olmalı (status=%s)", i + 1, allowed.getStatus())
					.isEqualTo(200);
		}
		EntityExchangeResult<byte[]> last = null;
		for (int i = 0; i < 8; i++) {
			last = getCart(token);
			if (last.getStatus().value() == 429) {
				return last;
			}
			assertThat(last.getStatus().value())
					.as("kota aşım denemesi %d beklenmedik status", i + 1)
					.isEqualTo(200);
		}
		return last;
	}

	private EntityExchangeResult<byte[]> postAuthLogin(String xForwardedFor) {
		return this.webTestClient.post()
				.uri("/api/auth/login")
				.header("X-Forwarded-For", xForwardedFor)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"email\":\"a@b.com\",\"password\":\"x\"}")
				.exchange()
				.expectBody()
				.returnResult();
	}

	private EntityExchangeResult<byte[]> getCart(String bearerToken) {
		return this.webTestClient.get()
				.uri("/api/cart")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
				.exchange()
				.expectBody()
				.returnResult();
	}

	private static String createJwt(RSAKey key, String subject, String role, Instant expiresAt) throws Exception {
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
				claimsSet);
		signedJWT.sign(signer);
		return signedJWT.serialize();
	}

}
