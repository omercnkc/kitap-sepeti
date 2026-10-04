package com.kitapsepeti.order.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.http.Fault;
import com.kitapsepeti.order.client.Downstream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sipariş yazıldıktan sonraki sonuçlar. Rezervasyon başarısızsa sipariş {@code failed} (stok {@code requested}),
 * Payment çağrılmaz; ödeme başlatılamazsa {@code failed} ve stok {@code held} kalır (bırakma Adım 5). Ödeme sonucu
 * bilinmiyorsa 201 ve sipariş {@code pending}. Hata yanıtlarında {@code orderId} var.
 */
@ExtendWith(OutputCaptureExtension.class)
class CheckoutAfterOrderFailuresTest extends CheckoutTestSupport {

	private final Book book = Book.of("Rezerv Kitabı", "45.00");

	private void stubUntilReserve() {
		stubCart(new Line(this.book, 2));
		stubLookup(this.book);
		stubPaymentInitiated(UUID.randomUUID(), "initiated");
	}

	// --- Rezervasyon başarısız: Payment çağrılmaz ---

	@Test
	void insufficientStockFailsOrderWithOutOfStock() throws Exception {
		stubUntilReserve();
		stubReserve(stockProblem("INSUFFICIENT_STOCK", this.book.id()));

		String body = expectFailedOrder(409, "INSUFFICIENT_STOCK", "OUT_OF_STOCK", "requested");

		assertThat(body).doesNotContain(this.book.id().toString());
	}

	@Test
	void notSellableAtReserveFailsOrderWithBookNotAvailable() throws Exception {
		stubUntilReserve();
		stubReserve(stockProblem("BOOK_NOT_AVAILABLE", this.book.id()));

		String body = expectFailedOrder(409, "BOOK_NOT_AVAILABLE", "BOOK_NOT_AVAILABLE", "requested");

		assertThat(body).doesNotContain(this.book.id().toString());
	}

	@Test
	void reserveNotPerformedFailsOrderWithCatalogUnavailable() throws Exception {
		stubUntilReserve();
		stubReserveHeld();
		// Lookup yanıtı gecikirken catalog devresi açılır: rezervasyon isteği hiç gönderilmez (NotPerformed).
		stubLookup(json(200, "{\"items\":[" + this.book.json() + "]}").withFixedDelay(500));
		CompletableFuture<Void> openCircuit = CompletableFuture.runAsync(() -> {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (CATALOG.server().findAll(getRequestedFor(urlPathEqualTo(LOOKUP))).isEmpty()
					&& System.nanoTime() < deadline) {
				Thread.onSpinWait();
			}
			this.breakers.get(Downstream.CATALOG).transitionToForcedOpenState();
		});

		ResultActions result = checkout();
		openCircuit.get(5, TimeUnit.SECONDS);

		expectFailedOrder(result, 503, "CATALOG_UNAVAILABLE", "CATALOG_UNAVAILABLE", "requested");
		assertThat(reserveRequests()).isEmpty();
	}

	@Test
	void reserveUnknownFailsOrderWithCatalogUnavailable() throws Exception {
		stubUntilReserve();
		stubReserve(problem(500, "INTERNAL_ERROR"));

		expectFailedOrder(503, "CATALOG_UNAVAILABLE", "CATALOG_UNAVAILABLE", "requested");
	}

	@Test
	void reserveConnectionDropIsUnknownAndFailsOrder() throws Exception {
		stubUntilReserve();
		stubReserve(json(200, "{}").withFault(Fault.CONNECTION_RESET_BY_PEER));

		expectFailedOrder(503, "CATALOG_UNAVAILABLE", "CATALOG_UNAVAILABLE", "requested");
	}

	@Test
	void reserveRejectedFailsOrderAndLogsError(CapturedOutput output) throws Exception {
		stubUntilReserve();
		stubReserve(problem(400, "VALIDATION_FAILED"));

		expectFailedOrder(503, "CATALOG_UNAVAILABLE", "CATALOG_UNAVAILABLE", "requested");

		assertThat(output).contains("ERROR")
			.contains("Checkout reserve rejected by catalog (status=400, code=VALIDATION_FAILED)");
	}

	@Test
	void reserveNotHeldFailsOrderAndLogsError(CapturedOutput output) throws Exception {
		stubUntilReserve();
		stubReserve(reservation(200, "released"));

		expectFailedOrder(503, "CATALOG_UNAVAILABLE", "CATALOG_UNAVAILABLE", "requested");

		assertThat(output).contains("Checkout reserve returned a reservation that is not held (status=RELEASED)");
	}

	// --- Ödeme başlatılamadı: stok held kalır ---

	@Test
	void paymentNotPerformedFailsOrderButStockStaysHeld() throws Exception {
		stubUntilReserve();
		stubReserveHeld();
		PAYMENT.stop();

		expectFailedOrder(503, "PAYMENT_UNAVAILABLE", "PAYMENT_UNAVAILABLE", "held");
	}

	@Test
	void paymentRejectedFailsOrderWithPaymentRejectedAndLogsError(CapturedOutput output) throws Exception {
		stubUntilReserve();
		stubReserveHeld();
		stubPayment(problem(409, "PAYMENT_ORDER_MISMATCH"));

		expectFailedOrder(503, "PAYMENT_UNAVAILABLE", "PAYMENT_REJECTED", "held");

		assertThat(output)
			.contains("Checkout payment rejected by payment service (status=409, code=PAYMENT_ORDER_MISMATCH)");
	}

	/** Sonuç bilinmiyor: Payment ödemeyi oluşturmuş olabilir; sipariş pending + held kalır (Adım 8 aynı istekle dener). */
	@Test
	void paymentUnknownKeepsOrderPendingAndReturns201(CapturedOutput output) throws Exception {
		stubUntilReserve();
		stubReserveHeld();
		stubPayment(problem(503, "PAYMENT_PROVIDER_UNAVAILABLE"));

		ResultActions result = checkout().andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("pending"))
			.andExpect(jsonPath("$.failureCode").isEmpty())
			.andExpect(jsonPath("$.paymentId").doesNotExist());
		UUID orderId = idOf(result);

		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("s", "pending").containsEntry("st", "held");
		assertThat(row.get("p")).isNull();
		assertThat(row.get("f")).isNull();
		assertThat(historyRows(orderId)).hasSize(1);
		assertThat(paymentRequests()).hasSize(1);
		assertThat(output).contains("WARN")
			.contains("Checkout payment outcome is unknown; order stays pending with stock held");
	}

	@Test
	void paymentReadTimeoutIsUnknownAndKeepsOrderPending() throws Exception {
		stubUntilReserve();
		stubReserveHeld();
		stubPayment(json(201, "{}").withFixedDelay(3_500));

		UUID orderId = idOf(checkout().andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("pending")));

		assertThat(orderRow(orderId)).containsEntry("s", "pending").containsEntry("st", "held");
	}

	/** Catalog devresi açıkken lookup yapılamaz: sipariş yazılmadan 503 (kayıt öncesi). */
	@Test
	void openCatalogCircuitFailsBeforeOrderIsWritten() throws Exception {
		stubUntilReserve();
		stubReserveHeld();
		this.breakers.get(Downstream.CATALOG).transitionToForcedOpenState();

		checkout().andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
			.andExpect(jsonPath("$.orderId").doesNotExist());
		assertThat(orderCount()).isZero();
	}

	private String expectFailedOrder(int httpStatus, String code, String failureCode, String stockState)
			throws Exception {
		return expectFailedOrder(checkout(), httpStatus, code, failureCode, stockState);
	}

	private String expectFailedOrder(ResultActions result, int httpStatus, String code, String failureCode,
			String stockState) throws Exception {
		result.andExpect(status().is(httpStatus))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.instance").value(CHECKOUT))
			.andExpect(jsonPath("$.orderId").isNotEmpty());
		UUID orderId = orderIdOf(result);

		assertThat(orderCount()).isEqualTo(1);
		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("u", this.userId.toString())
			.containsEntry("s", "failed")
			.containsEntry("f", failureCode)
			.containsEntry("st", stockState);
		assertThat(row.get("p")).isNull();
		List<Map<String, Object>> history = historyRows(orderId);
		assertThat(history).hasSize(2);
		assertThat(history.get(0).get("fs")).isNull();
		assertThat(history.get(0)).containsEntry("ts", "pending").containsEntry("r", "ORDER_PLACED");
		assertThat(history.get(1)).containsEntry("fs", "pending").containsEntry("ts", "failed").containsEntry("r",
				failureCode);
		if ("requested".equals(stockState)) {
			assertThat(paymentRequests()).isEmpty();
		}
		else {
			assertThat(reserveRequests()).hasSize(1);
		}
		return result.andReturn().getResponse().getContentAsString();
	}

}
