package com.kitapsepeti.order.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.kitapsepeti.order.gateway.PaymentInitiationResult;
import com.kitapsepeti.order.gateway.PaymentState;
import com.kitapsepeti.order.gateway.Rejected;
import com.kitapsepeti.order.gateway.Unknown;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Payment {@code POST /internal/payments} eşlemesi (docs/api/payment-service.openapi.json). */
class PaymentGatewayTest extends ClientTestSupport {

	private static final String PATH = "/internal/payments";

	private static final BigDecimal AMOUNT = new BigDecimal("149.90");

	private final UUID orderId = UUID.randomUUID();

	private final UUID userId = UUID.randomUUID();

	private final UUID paymentId = UUID.randomUUID();

	private String payment(UUID order, String status) {
		return """
				{"paymentId":"%s","orderId":"%s","amount":149.90,"currency":"TRY","status":"%s","failureCode":null,
				 "redirectUrl":"https://provider.example/pay","createdAt":"2026-10-04T10:00:00Z",
				 "updatedAt":"2026-10-04T10:00:00Z","newField":true}"""
			.formatted(this.paymentId, order, status);
	}

	private PaymentInitiationResult initiate() {
		return this.paymentGateway.initiate(this.orderId, this.userId, AMOUNT, "TRY");
	}

	private static void respond(ResponseDefinitionBuilder response) {
		PAYMENT.server().stubFor(post(urlEqualTo(PATH)).willReturn(response));
	}

	@Test
	void createdPaymentIsInitiatedAndBodyMatchesContract() {
		respond(json(201, payment(this.orderId, "initiated")));

		assertThat(initiate()).isEqualTo(new PaymentInitiationResult.Initiated(this.paymentId, PaymentState.INITIATED));
		PAYMENT.server()
			.verify(1, postRequestedFor(urlEqualTo(PATH)).withHeader(InternalApiKey.HEADER, equalTo(API_KEY))
				.withRequestBody(equalToJson("""
						{"orderId":"%s","userId":"%s","amount":149.90,"currency":"TRY"}"""
					.formatted(this.orderId, this.userId))));
		assertThat(PAYMENT.server().findAll(postRequestedFor(urlEqualTo(PATH))).getFirst().getBodyAsString())
			.contains("\"amount\":149.90");
	}

	@ParameterizedTest
	@CsvSource({ "initiated, INITIATED", "succeeded, SUCCEEDED", "failed, FAILED" })
	void replayedPaymentCarriesItsState(String status, PaymentState state) {
		respond(json(200, payment(this.orderId, status)));

		assertThat(initiate()).isEqualTo(new PaymentInitiationResult.Initiated(this.paymentId, state));
	}

	@Test
	void failedPaymentCarriesItsFailureCode() {
		respond(json(200, payment(this.orderId, "failed").replace("\"failureCode\":null", "\"failureCode\":\"CARD_DECLINED\"")));

		assertThat(initiate())
			.isEqualTo(new PaymentInitiationResult.Initiated(this.paymentId, PaymentState.FAILED, "CARD_DECLINED"));
	}

	@ParameterizedTest
	@CsvSource({ "409, PAYMENT_ORDER_MISMATCH", "409, CONFLICT", "400, VALIDATION_FAILED", "400, MALFORMED_REQUEST",
			"401, UNAUTHORIZED" })
	void problemsAreRejectedWithCode(int status, String code) {
		respond(problem(status, code));

		assertThat(initiate()).isEqualTo(new Rejected(status, code));
	}

	/** Payment satırı oluşmuş olabilir; aynı istekle tekrar tamamlar. */
	@Test
	void providerUnavailableIsUnknown() {
		respond(problem(503, "PAYMENT_PROVIDER_UNAVAILABLE"));

		assertThat(initiate()).isEqualTo(new Unknown());
	}

	@Test
	void internalErrorIsUnknown() {
		respond(problem(500, "INTERNAL_ERROR"));

		assertThat(initiate()).isEqualTo(new Unknown());
	}

	@Test
	void readTimeoutIsUnknown() {
		respond(slow(json(201, payment(this.orderId, "initiated"))));

		assertThat(initiate()).isEqualTo(new Unknown());
	}

	@ParameterizedTest
	@ValueSource(strings = { "{not json", "{\"orderId\":\"%2$s\",\"status\":\"initiated\"}",
			"{\"paymentId\":\"%1$s\",\"orderId\":\"%2$s\"}",
			"{\"paymentId\":\"%1$s\",\"orderId\":\"%2$s\",\"status\":\"refunded\",\"failureCode\":null}",
			"{\"paymentId\":\"%1$s\",\"orderId\":\"%3$s\",\"status\":\"initiated\",\"failureCode\":null}",
			"{\"paymentId\":\"%1$s\",\"orderId\":\"%2$s\",\"status\":\"initiated\"}" })
	void malformedForeignOrUnknownStatusIsUnknown(String body) {
		respond(json(201, body.formatted(this.paymentId, this.orderId, UUID.randomUUID())));

		assertThat(initiate()).isEqualTo(new Unknown());
	}

}
