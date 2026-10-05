package com.kitapsepeti.gateway.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsConfigurationTest {

	private static WireMockServer wireMock;

	@LocalServerPort
	private int port;

	private WebTestClient webTestClient;

	@BeforeAll
	static void startWireMock() {
		wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
		wireMock.start();
	}

	@AfterAll
	static void stopWireMock() {
		if (wireMock != null) {
			wireMock.stop();
		}
	}

	@DynamicPropertySource
	static void configureDownstream(DynamicPropertyRegistry registry) {
		registry.add("CATALOG_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
	}

	@BeforeEach
	void setUp() {
		this.webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + this.port).build();
		wireMock.resetAll();
	}

	@Test
	void preflightCorsRequest_shouldReturn200AndExpectedCorsHeaders() {
		EntityExchangeResult<byte[]> result = this.webTestClient.options()
				.uri("/api/books")
				.header(HttpHeaders.ORIGIN, "http://localhost:4200")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type,X-Request-Id")
				.exchange()
				.expectStatus()
				.isOk()
				.expectHeader()
				.valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
				.expectHeader()
				.valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
				.expectHeader()
				.valueEquals(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600")
				.expectBody()
				.returnResult();

		HttpHeaders headers = result.getResponseHeaders();
		assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("GET");
		assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).contains("Authorization");
	}

	@Test
	void actualCorsRequest_shouldExposeConfiguredHeaders() {
		wireMock.stubFor(get(urlEqualTo("/api/books"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("[]")));

		EntityExchangeResult<byte[]> result = this.webTestClient.get()
				.uri("/api/books")
				.header(HttpHeaders.ORIGIN, "http://localhost:4200")
				.exchange()
				.expectStatus()
				.isOk()
				.expectHeader()
				.valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
				.expectHeader()
				.valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
				.expectBody()
				.returnResult();

		HttpHeaders headers = result.getResponseHeaders();
		assertThat(headers.getAccessControlExposeHeaders()).contains("X-Request-Id", "Authorization", "Location");
	}

	@Test
	void disallowedOrigin_shouldNotAllowCors() {
		this.webTestClient.options()
				.uri("/api/books")
				.header(HttpHeaders.ORIGIN, "http://evil-attacker.com")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
				.exchange()
				.expectStatus()
				.isForbidden();
	}

}
