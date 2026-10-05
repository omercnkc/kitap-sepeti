package com.kitapsepeti.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
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
class WebhookRouteTest {

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
		registry.add("PAYMENT_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
	}

	@BeforeEach
	void setUp() {
		this.webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + this.port).build();
		wireMock.resetAll();
	}

	@Test
	void webhookPost_withoutJwt_shouldForwardDirectlyToPaymentService() {
		wireMock.stubFor(post(urlEqualTo("/webhooks/payment"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"status\":\"processed\"}")));

		this.webTestClient.post()
				.uri("/webhooks/payment")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"paymentId\":\"pay-123\",\"status\":\"success\"}")
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo("processed");

		List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlEqualTo("/webhooks/payment")));
		assertThat(requests).hasSize(1);
		LoggedRequest received = requests.get(0);
		assertThat(received.getHeader("X-Request-Id")).isNotBlank();
		assertThat(received.getBodyAsString()).contains("pay-123");
	}

	@Test
	void webhookMockPost_withoutJwt_shouldForwardToPaymentRoute() {
		wireMock.stubFor(post(urlEqualTo("/webhooks/mock"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"accepted\":true}")));

		this.webTestClient.post()
				.uri("/webhooks/mock")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"eventId\":\"evt-1\",\"status\":\"succeeded\"}")
				.exchange()
				.expectStatus()
				.isOk()
				.expectHeader()
				.exists("X-Request-Id")
				.expectBody()
				.jsonPath("$.accepted")
				.isEqualTo(true);

		List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlEqualTo("/webhooks/mock")));
		assertThat(requests).hasSize(1);
		assertThat(requests.get(0).getHeader("X-Request-Id")).isNotBlank();
	}

	@Test
	void apiWebhookPost_withoutJwt_shouldForwardDirectlyToPaymentService() {
		wireMock.stubFor(post(urlEqualTo("/api/webhooks/mock"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"status\":\"ok\"}")));

		this.webTestClient.post()
				.uri("/api/webhooks/mock")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"event\":\"payment.succeeded\"}")
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo("ok");

		List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlEqualTo("/api/webhooks/mock")));
		assertThat(requests).hasSize(1);
		assertThat(requests.get(0).getHeader("X-Request-Id")).isNotBlank();
	}

}
