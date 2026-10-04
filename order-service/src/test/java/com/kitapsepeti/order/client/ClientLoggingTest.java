package com.kitapsepeti.order.client;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.order.gateway.StockLine;
import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.core.env.Environment;

/**
 * Feign yapılandırması (log kapalı, tekrar yok, istemciye özel interceptor) ve gateway log satırlarının içeriği:
 * istemci, işlem, sonuç tipi, durum ve süre var; id, tutar, kitap, gövde, anahtar ve URL yolu yok.
 */
@ExtendWith(OutputCaptureExtension.class)
class ClientLoggingTest extends ClientTestSupport {

	private static final List<String> CLIENTS = List.of("cart", "catalog", "payment");

	@Autowired
	private FeignClientFactory feignClientFactory;

	@Autowired
	private FeignClientProperties feignClientProperties;

	@Autowired
	private Environment environment;

	@Test
	void feignLoggingIsOffRetryIsNeverAndEachClientHasOnlyItsOwnInterceptor() {
		for (String name : CLIENTS) {
			FeignClientProperties.FeignClientConfiguration config = this.feignClientProperties.getConfig().get(name);
			assertThat(config.getLoggerLevel()).as(name).isEqualTo(Logger.Level.NONE);
			assertThat(config.getConnectTimeout()).as(name).isEqualTo(1000);
			assertThat(config.getReadTimeout()).as(name).isEqualTo(3000);
			assertThat(config.getRetryer()).as(name).isNull();
			assertThat(config.getRequestInterceptors()).as(name).isNullOrEmpty();
			assertThat(this.feignClientFactory.getInstance(name, Logger.Level.class)).as(name).isEqualTo(Logger.Level.NONE);
			assertThat(this.feignClientFactory.getInstance(name, Retryer.class)).as(name).isSameAs(Retryer.NEVER_RETRY);
			assertThat(this.feignClientFactory.getInstance(name, ErrorDecoder.class)).as(name)
				.isInstanceOf(ProblemErrorDecoder.class);
			assertThat(this.feignClientFactory.getInstances(name, RequestInterceptor.class).values()).as(name)
				.singleElement()
				.isInstanceOf(InternalApiKeyInterceptor.class);
		}
		assertThat(this.feignClientProperties.getConfig().get("default").getLoggerLevel()).isEqualTo(Logger.Level.NONE);
		assertThat(this.environment.getProperty("spring.cloud.openfeign.circuitbreaker.enabled")).isEqualTo("false");
	}

	@Test
	void logLinesCarryNoIdentifiersAmountsKeyOrPaths(CapturedOutput output) {
		UUID orderId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID cartId = UUID.randomUUID();
		UUID bookId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		CART.server()
			.stubFor(post(urlEqualTo("/internal/cart/snapshot")).willReturn(json(200, """
					{"cartId":"%s","updatedAt":null,"items":[{"bookId":"%s","quantity":3}]}""".formatted(cartId, bookId))));
		CATALOG.server()
			.stubFor(get(urlPathEqualTo("/api/books/lookup")).willReturn(json(200, """
					{"items":[{"id":"%s","title":"Gizli Baslik","priceAmount":987.65,"currency":"TRY","inStock":true}]}"""
				.formatted(bookId))));
		CATALOG.server()
			.stubFor(post(urlEqualTo("/internal/stock/reservations"))
				.willReturn(stockProblem("INSUFFICIENT_STOCK", bookId)));
		CATALOG.server()
			.stubFor(post(urlEqualTo("/internal/stock/reservations/" + orderId + "/commit"))
				.willReturn(json(200, "{\"orderId\":\"%s\",\"status\":\"committed\",\"expiresAt\":\"2026-10-04T10:15:00Z\"}"
					.formatted(orderId))));
		CATALOG.server()
			.stubFor(post(urlEqualTo("/internal/stock/reservations/" + orderId + "/release"))
				.willReturn(problem(500, "INTERNAL_ERROR")));
		PAYMENT.server()
			.stubFor(post(anyUrl()).willReturn(json(201, """
					{"paymentId":"%s","orderId":"%s","status":"initiated","amount":987.65}""".formatted(paymentId, orderId))));
		int from = output.getAll().length();

		this.cartGateway.snapshot(userId);
		this.catalogGateway.lookup(List.of(bookId));
		this.catalogGateway.reserve(orderId, List.of(new StockLine(bookId, 3)));
		this.catalogGateway.commit(orderId);
		this.catalogGateway.release(orderId);
		this.paymentGateway.initiate(orderId, userId, new BigDecimal("987.65"), "TRY");
		CATALOG.stop();
		this.catalogGateway.commit(orderId);

		String log = output.getAll().substring(from);
		assertThat(log).contains("Remote call cart snapshot -> Snapshot (status=200, durationMs=")
			.contains("Remote call catalog lookup -> Found (status=200, durationMs=")
			.contains("Remote call catalog reserve -> Insufficient (status=409, durationMs=")
			.contains("Remote call catalog commit -> Committed (status=200, durationMs=")
			.contains("Remote call catalog release -> Unknown (status=500, durationMs=")
			.contains("Remote call payment initiate -> Initiated (status=201, durationMs=")
			.contains("Remote call catalog commit -> NotPerformed (status=-, durationMs=");
		for (UUID id : List.of(orderId, userId, cartId, bookId, paymentId)) {
			assertThat(log).doesNotContain(id.toString());
		}
		assertThat(log).doesNotContain("987.65")
			.doesNotContain("Gizli Baslik")
			.doesNotContain(API_KEY)
			.doesNotContain(InternalApiKey.HEADER)
			.doesNotContain("/internal/")
			.doesNotContain("/api/books")
			.doesNotContain(CATALOG.baseUrl())
			.doesNotContain("INSUFFICIENT_STOCK\",\"")
			.doesNotContain("Exception:");
	}

}
