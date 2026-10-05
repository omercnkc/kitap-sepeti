package com.kitapsepeti.gateway.filter;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InternalPathBlockingTest {

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
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("CATALOG_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("CART_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
	}

	@BeforeEach
	void setUp() {
		this.webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + this.port).build();
		wireMock.resetAll();
	}

	@Test
	void internalRootPath_shouldReturn404AndNotReachDownstream() {
		this.webTestClient.get()
				.uri("/internal/stock/check")
				.exchange()
				.expectStatus()
				.isNotFound()
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(404)
				.jsonPath("$.code")
				.isEqualTo("NOT_FOUND");

		assertThat(wireMock.findAll(getRequestedFor(urlMatching(".*")))).isEmpty();
	}

	@Test
	void nestedInternalPath_shouldReturn404AndNotReachDownstream() {
		this.webTestClient.get()
				.uri("/api/cart/internal/snapshot")
				.exchange()
				.expectStatus()
				.isNotFound()
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(404)
				.jsonPath("$.code")
				.isEqualTo("NOT_FOUND");

		assertThat(wireMock.findAll(getRequestedFor(urlMatching(".*")))).isEmpty();
	}

	@Test
	void apiInternalTest_shouldReturn404AndNotReachDownstream() {
		this.webTestClient.get()
				.uri("/api/internal/test")
				.exchange()
				.expectStatus()
				.isNotFound()
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(404)
				.jsonPath("$.code")
				.isEqualTo("NOT_FOUND");

		assertThat(wireMock.findAll(getRequestedFor(urlMatching(".*")))).isEmpty();
	}

}
