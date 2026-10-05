package com.kitapsepeti.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
		"app.jwt.enabled=false"
})
class RouteConfigurationTest {

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
		registry.add("USER_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("CATALOG_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("CART_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("ORDER_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("SEARCH_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
		registry.add("NOTIFICATION_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
	}

	@BeforeEach
	void setUp() {
		this.webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + this.port).build();
		wireMock.resetAll();
	}

	@Test
	void healthEndpointReturnsUp() {
		this.webTestClient.get()
			.uri("/actuator/health")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.status")
			.isEqualTo("UP");
	}

	@Test
	void catalogRouteForwardsBooksToCatalogService() {
		wireMock.stubFor(get(urlEqualTo("/api/books/test-id"))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"title\":\"Deneme Kitabi\"}")));

		this.webTestClient.get()
			.uri("/api/books/test-id")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.title")
			.isEqualTo("Deneme Kitabi");
	}

	@Test
	void catalogRouteForwardsCategoriesToCatalogService() {
		wireMock.stubFor(get(urlEqualTo("/api/categories"))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("[{\"name\":\"Roman\"}]")));

		this.webTestClient.get()
			.uri("/api/categories")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$[0].name")
			.isEqualTo("Roman");
	}

	@Test
	void userRouteForwardsAuthToUserService() {
		wireMock.stubFor(get(urlEqualTo("/api/auth/me"))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"email\":\"test@kitapsepeti.com\"}")));

		this.webTestClient.get()
			.uri("/api/auth/me")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.email")
			.isEqualTo("test@kitapsepeti.com");
	}

	@Test
	void cartRouteForwardsCartToCartService() {
		wireMock.stubFor(get(urlEqualTo("/api/cart"))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"items\":[]}")));

		this.webTestClient.get()
			.uri("/api/cart")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items")
			.isArray();
	}

	@Test
	void orderRouteForwardsOrdersToOrderService() {
		wireMock.stubFor(get(urlEqualTo("/api/orders/order-1"))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"status\":\"placed\"}")));

		this.webTestClient.get()
			.uri("/api/orders/order-1")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.status")
			.isEqualTo("placed");
	}

	@Test
	void userRouteForwardsMeToUserService() {
		wireMock.stubFor(get(urlEqualTo("/api/me/addresses"))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("[]")));

		this.webTestClient.get()
			.uri("/api/me/addresses")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$")
			.isArray();
	}

	@Test
	void searchRouteForwardsSearchToSearchService() {
		wireMock.stubFor(get(urlEqualTo("/api/search?q=kitap"))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"results\":[]}")));

		this.webTestClient.get()
			.uri("/api/search?q=kitap")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.results")
			.isArray();
	}

	@Test
	void searchRouteForwardsSuggestToSearchService() {
		wireMock.stubFor(get(urlEqualTo("/api/suggest?prefix=kit"))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("[\"kitap\"]")));

		this.webTestClient.get()
			.uri("/api/suggest?prefix=kit")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$[0]")
			.isEqualTo("kitap");
	}

	@Test
	void notificationRouteForwardsNotificationsToNotificationService() {
		wireMock.stubFor(get(urlEqualTo("/api/notifications"))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("[]")));

		this.webTestClient.get()
			.uri("/api/notifications")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$")
			.isArray();
	}

	@Test
	void notificationRouteForwardsPreferencesToNotificationService() {
		wireMock.stubFor(get(urlEqualTo("/api/preferences"))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"emailEnabled\":true}")));

		this.webTestClient.get()
			.uri("/api/preferences")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.emailEnabled")
			.isEqualTo(true);
	}

}
