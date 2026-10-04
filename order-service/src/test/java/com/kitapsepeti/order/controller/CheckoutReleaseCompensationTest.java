package com.kitapsepeti.order.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.kitapsepeti.order.client.Downstream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Kayıt sonrası başarısızlıklarda satır içi stok serbest bırakma (Catalog release) matrisi.
 * HTTP yanıtı ve kodları etkilenmez; release sonucuna göre DB'deki stock_state {@code released} olur ya da
 * {@code requested}/{@code held} kalır (Adım 6 ve 8 uzlaştırması için).
 */
@ExtendWith(OutputCaptureExtension.class)
class CheckoutReleaseCompensationTest extends CheckoutTestSupport {

	private final Book book = Book.of("Telafi Kitabı", "85.00");

	private boolean openCatalogBeforeRelease;

	private boolean resetCatalogOnMarkFailed;

	@BeforeEach
	void resetTestFlags() {
		this.openCatalogBeforeRelease = false;
		this.resetCatalogOnMarkFailed = false;
	}

	enum FailureType {

		RESERVE_INSUFFICIENT(409, "INSUFFICIENT_STOCK", "OUT_OF_STOCK", false, "requested"),
		RESERVE_NOT_SELLABLE(409, "BOOK_NOT_AVAILABLE", "BOOK_NOT_AVAILABLE", false, "requested"),
		RESERVE_NOT_HELD(503, "CATALOG_UNAVAILABLE", "CATALOG_UNAVAILABLE", false, "requested"),
		RESERVE_REJECTED(503, "CATALOG_UNAVAILABLE", "CATALOG_UNAVAILABLE", false, "requested"),
		RESERVE_NOT_PERFORMED(503, "CATALOG_UNAVAILABLE", "CATALOG_UNAVAILABLE", false, "requested"),
		RESERVE_UNKNOWN(503, "CATALOG_UNAVAILABLE", "CATALOG_UNAVAILABLE", false, "requested"),
		PAYMENT_NOT_PERFORMED(503, "PAYMENT_UNAVAILABLE", "PAYMENT_UNAVAILABLE", true, "held"),
		PAYMENT_REJECTED(503, "PAYMENT_UNAVAILABLE", "PAYMENT_REJECTED", true, "held");

		final int httpStatus;
		final String code;
		final String failureCode;
		final boolean isPayment;
		final String initialStockState;

		FailureType(int httpStatus, String code, String failureCode, boolean isPayment, String initialStockState) {
			this.httpStatus = httpStatus;
			this.code = code;
			this.failureCode = failureCode;
			this.isPayment = isPayment;
			this.initialStockState = initialStockState;
		}

	}

	enum ReleaseType {

		RELEASED("released", "INFO", "Released"),
		ALREADY_COMMITTED(null, "ERROR", "AlreadyCommitted"),
		NOT_PERFORMED(null, "WARN", "NotPerformed"),
		UNKNOWN(null, "WARN", "Unknown"),
		REJECTED(null, "WARN", "Rejected");

		final String targetStockState;
		final String expectedLogLevel;
		final String expectedLogResultName;

		ReleaseType(String targetStockState, String expectedLogLevel, String expectedLogResultName) {
			this.targetStockState = targetStockState;
			this.expectedLogLevel = expectedLogLevel;
			this.expectedLogResultName = expectedLogResultName;
		}

	}

	static Stream<Arguments> compensationMatrix() {
		List<Arguments> combinations = new ArrayList<>();
		for (FailureType failure : FailureType.values()) {
			for (ReleaseType release : ReleaseType.values()) {
				combinations.add(Arguments.of(failure, release));
			}
		}
		return combinations.stream();
	}

	@ParameterizedTest(name = "{0} x {1}")
	@MethodSource("compensationMatrix")
	@DisplayName("Kayıt sonrası başarısızlık x release sonucu matrisi")
	void compensationMatrixTests(FailureType failure, ReleaseType release, CapturedOutput output) throws Exception {
		stubCart(new Line(this.book, 1));
		stubLookup(this.book);
		setupFailureStub(failure);
		setupReleaseStub(release);

		ResultActions result = checkout().andExpect(status().is(failure.httpStatus))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value(failure.code))
			.andExpect(jsonPath("$.instance").value(CHECKOUT))
			.andExpect(jsonPath("$.orderId").isNotEmpty());

		UUID orderId = orderIdOf(result);
		assertThat(orderCount()).isEqualTo(1);
		if (release != ReleaseType.NOT_PERFORMED) {
			assertThat(releaseRequests()).hasSize(1);
		}

		String expectedStock = release.targetStockState != null ? release.targetStockState : failure.initialStockState;
		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("s", "failed")
			.containsEntry("f", failure.failureCode)
			.containsEntry("st", expectedStock);

		assertThat(output).contains(release.expectedLogLevel)
			.contains("Stock compensation -> " + release.expectedLogResultName);
	}

	/** Insufficient senaryosu: Catalog release'e 404 RESOURCE_NOT_FOUND → stock_state released. */
	@Test
	void insufficientStockReleaseNotFoundBecomesReleased() throws Exception {
		stubCart(new Line(this.book, 1));
		stubLookup(this.book);
		stubReserve(stockProblem("INSUFFICIENT_STOCK", this.book.id()));
		stubReleaseNotFound();

		ResultActions result = checkout().andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
		UUID orderId = orderIdOf(result);

		assertThat(releaseRequests()).hasSize(1);
		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("s", "failed")
			.containsEntry("f", "OUT_OF_STOCK")
			.containsEntry("st", "released");
	}

	/** Telafi loglarında id, tutar, kitap id'si veya hassas bilgi bulunmaz. */
	@Test
	void compensationLogsContainNoSensitiveIdentifiers(CapturedOutput output) throws Exception {
		stubCart(new Line(this.book, 1));
		stubLookup(this.book);
		stubReserve(stockProblem("INSUFFICIENT_STOCK", this.book.id()));
		stubReleaseReleased();

		UUID orderId = orderIdOf(checkout().andExpect(status().isConflict()));

		String logs = output.getAll();
		assertThat(logs).contains("Stock compensation -> Released")
			.doesNotContain(orderId.toString())
			.doesNotContain(this.userId.toString())
			.doesNotContain(this.book.id().toString())
			.doesNotContain(this.book.title())
			.doesNotContain(RECIPIENT)
			.doesNotContain(PHONE)
			.doesNotContain(CITY);
	}

	private void setupFailureStub(FailureType failure) {
		switch (failure) {
			case RESERVE_INSUFFICIENT -> stubReserve(stockProblem("INSUFFICIENT_STOCK", this.book.id()));
			case RESERVE_NOT_SELLABLE -> stubReserve(stockProblem("BOOK_NOT_AVAILABLE", this.book.id()));
			case RESERVE_NOT_HELD -> stubReserve(reservation(200, "released"));
			case RESERVE_REJECTED -> stubReserve(problem(400, "VALIDATION_FAILED"));
			case RESERVE_NOT_PERFORMED -> {
				stubReserveHeld();
				this.resetCatalogOnMarkFailed = true;
				stubLookup(json(200, "{\"items\":[" + this.book.json() + "]}").withFixedDelay(300));
				CompletableFuture.runAsync(() -> {
					long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
					while (CATALOG.server().findAll(getRequestedFor(urlPathEqualTo(LOOKUP))).isEmpty()
							&& System.nanoTime() < deadline) {
						Thread.onSpinWait();
					}
					this.breakers.get(Downstream.CATALOG).transitionToForcedOpenState();
				});
			}
			case RESERVE_UNKNOWN -> stubReserve(problem(500, "INTERNAL_ERROR"));
			case PAYMENT_NOT_PERFORMED -> {
				stubReserveHeld();
				PAYMENT.stop();
			}
			case PAYMENT_REJECTED -> {
				stubReserveHeld();
				stubPayment(problem(409, "PAYMENT_ORDER_MISMATCH"));
			}
		}
	}

	private void setupReleaseStub(ReleaseType release) {
		if (release == ReleaseType.NOT_PERFORMED) {
			this.openCatalogBeforeRelease = true;
		}
		Mockito.doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			if (this.resetCatalogOnMarkFailed) {
				this.breakers.get(Downstream.CATALOG).reset();
			}
			if (this.openCatalogBeforeRelease) {
				this.breakers.get(Downstream.CATALOG).transitionToForcedOpenState();
			}
			return result;
		}).when(this.transactions).markFailed(any(), any());

		switch (release) {
			case RELEASED -> stubReleaseReleased();
			case ALREADY_COMMITTED -> stubRelease(problem(409, "RESERVATION_COMMITTED"));
			case NOT_PERFORMED -> stubReleaseReleased(); // devre açık olduğu için istek gitmeyecek
			case UNKNOWN -> stubRelease(problem(500, "INTERNAL_ERROR"));
			case REJECTED -> stubRelease(problem(400, "VALIDATION_FAILED"));
		}
	}

}
