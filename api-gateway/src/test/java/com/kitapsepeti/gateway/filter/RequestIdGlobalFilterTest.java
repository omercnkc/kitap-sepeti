package com.kitapsepeti.gateway.filter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestIdGlobalFilterTest {

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
	void whenNoRequestIdProvided_shouldGenerateUuidAndForwardBothToDownstreamAndClientResponse() {
		wireMock.stubFor(get(urlEqualTo("/api/books/123"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"id\":123}")));

		EntityExchangeResult<byte[]> result = this.webTestClient.get()
				.uri("/api/books/123")
				.exchange()
				.expectStatus()
				.isOk()
				.expectHeader()
				.exists("X-Request-Id")
				.expectBody()
				.returnResult();

		String responseRequestId = result.getResponseHeaders().getFirst("X-Request-Id");
		assertThat(responseRequestId).isNotBlank();
		// Assert it is a valid UUID format
		UUID.fromString(responseRequestId);

		List<LoggedRequest> requests = wireMock.findAll(getRequestedFor(urlEqualTo("/api/books/123")));
		assertThat(requests).hasSize(1);
		String downstreamRequestId = requests.get(0).getHeader("X-Request-Id");
		assertThat(downstreamRequestId).isEqualTo(responseRequestId);
	}

	@Test
	void whenRequestIdProvided_shouldPreserveSameIdBothInDownstreamAndClientResponse() {
		String customRequestId = "test-trace-123";

		wireMock.stubFor(get(urlEqualTo("/api/books/456"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"id\":456}")));

		EntityExchangeResult<byte[]> result = this.webTestClient.get()
				.uri("/api/books/456")
				.header("X-Request-Id", customRequestId)
				.exchange()
				.expectStatus()
				.isOk()
				.expectHeader()
				.valueEquals("X-Request-Id", customRequestId)
				.expectBody()
				.returnResult();

		String responseRequestId = result.getResponseHeaders().getFirst("X-Request-Id");
		assertThat(responseRequestId).isEqualTo(customRequestId);

		List<LoggedRequest> requests = wireMock.findAll(getRequestedFor(urlEqualTo("/api/books/456")));
		assertThat(requests).hasSize(1);
		String downstreamRequestId = requests.get(0).getHeader("X-Request-Id");
		assertThat(downstreamRequestId).isEqualTo(customRequestId);
	}

}
