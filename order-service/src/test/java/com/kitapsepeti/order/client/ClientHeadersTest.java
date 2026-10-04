package com.kitapsepeti.order.client;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.kitapsepeti.order.gateway.StockLine;
import com.kitapsepeti.order.support.StubServer;
import com.kitapsepeti.order.support.TestJwt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Giden isteklerin başlıkları: internal uçlarda yalnızca servis anahtarı; kullanıcı isteğinin Authorization/Cookie
 * başlıkları ve SecurityContext'teki token hiçbir isteğe taşınmaz (gateway bir kullanıcı isteğinin içinden çağrılıyormuş
 * gibi bağlam kurulur).
 */
class ClientHeadersTest extends ClientTestSupport {

	private final UUID orderId = UUID.randomUUID();

	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
		SecurityContextHolder.clearContext();
	}

	@Test
	void onlyTheServiceKeyIsSentAndUserCredentialsNeverLeave() {
		String token = TestJwt.user(SUBJECT);
		MockHttpServletRequest incoming = new MockHttpServletRequest("POST", "/api/orders");
		incoming.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		incoming.addHeader(HttpHeaders.COOKIE, "SESSION=abc");
		incoming.addHeader("X-Forwarded-For", "203.0.113.7");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(SUBJECT, token, "ROLE_USER"));
		stubEverything();

		this.cartGateway.snapshot(UUID.randomUUID());
		this.catalogGateway.lookup(List.of(UUID.randomUUID()));
		this.catalogGateway.reserve(this.orderId, List.of(new StockLine(UUID.randomUUID(), 1)));
		this.catalogGateway.commit(this.orderId);
		this.catalogGateway.release(this.orderId);
		this.paymentGateway.initiate(this.orderId, UUID.randomUUID(), BigDecimal.ONE, "TRY");

		List<LoggedRequest> requests = new ArrayList<>();
		for (StubServer stub : STUBS) {
			requests.addAll(stub.server().findAll(anyRequestedFor(anyUrl())));
		}
		assertThat(requests).hasSize(6).allSatisfy(request -> {
			assertThat(request.containsHeader(HttpHeaders.AUTHORIZATION)).as("Authorization").isFalse();
			assertThat(request.containsHeader(HttpHeaders.COOKIE)).as("Cookie").isFalse();
			assertThat(request.containsHeader("X-Forwarded-For")).isFalse();
			assertThat(request.getAllHeaderKeys().toString() + request.getBodyAsString() + request.getUrl())
				.doesNotContain(token);
			if (request.getUrl().startsWith("/internal/")) {
				assertThat(request.getHeaders().getHeader(InternalApiKey.HEADER).values()).containsExactly(API_KEY);
			}
			else {
				assertThat(request.containsHeader(InternalApiKey.HEADER)).as("public lookup").isFalse();
			}
		});
		for (LoggedRequest request : requests) {
			for (String name : request.getAllHeaderKeys()) {
				assertThat(request.getHeader(name)).doesNotContain(token);
			}
		}
	}

	private void stubEverything() {
		CART.server()
			.stubFor(post(urlEqualTo("/internal/cart/snapshot"))
				.willReturn(json(200, "{\"cartId\":null,\"updatedAt\":null,\"items\":[]}")));
		CATALOG.server()
			.stubFor(get(urlPathEqualTo("/api/books/lookup")).willReturn(json(200, "{\"items\":[]}")));
		CATALOG.server().stubFor(post(anyUrl()).willReturn(problem(500, "INTERNAL_ERROR")));
		PAYMENT.server().stubFor(post(anyUrl()).willReturn(problem(500, "INTERNAL_ERROR")));
	}

}
