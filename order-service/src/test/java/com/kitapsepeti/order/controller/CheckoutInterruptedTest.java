package com.kitapsepeti.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import com.kitapsepeti.order.entity.OrderReasons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checkout sırasında OrderTransactions işlemlerinde beklenmeyen hata (DB kesintisi, lock timeout vb.) senaryoları.
 * Kural: Para işin içindeyse failed yapılmaz; stok işin içindeyse hemen release denenir.
 */
@ExtendWith(OutputCaptureExtension.class)
class CheckoutInterruptedTest extends CheckoutTestSupport {

	private final Book book = Book.of("Kesinti Kitabı", "120.00");

	// --- 2.a: Rezervasyon başarılı ama markStockHeld başarısız ---

	@Test
	@DisplayName("2.a: markStockHeld başarısız, markFailed başarılı -> 503 CHECKOUT_INTERRUPTED + release yapıldı")
	void markStockHeldFailsAndMarkFailedSucceeds(CapturedOutput output) throws Exception {
		stubHappyPath();
		stubReleaseReleased();
		doThrow(new CannotAcquireLockException("Lock timeout")).when(this.transactions).markStockHeld(any());

		ResultActions result = checkout().andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("CHECKOUT_INTERRUPTED"))
			.andExpect(jsonPath("$.orderId").isNotEmpty());

		UUID orderId = orderIdOf(result);
		assertThat(releaseRequests()).hasSize(1);
		assertThat(paymentRequests()).isEmpty();

		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("s", "failed")
			.containsEntry("f", "CHECKOUT_INTERRUPTED")
			.containsEntry("st", "released");
		assertThat(outboxTypes(orderId)).containsExactly("OrderFailed");

		assertCleanLogs(output, orderId);
	}

	@Test
	@DisplayName("2.a: markStockHeld ve markFailed başarısız -> 503 CHECKOUT_INTERRUPTED, DB pending+requested")
	void markStockHeldAndMarkFailedBothFail(CapturedOutput output) throws Exception {
		stubHappyPath();
		stubReleaseReleased();
		doThrow(new CannotAcquireLockException("Lock timeout")).when(this.transactions).markStockHeld(any());
		doThrow(new CannotAcquireLockException("Lock timeout")).when(this.transactions)
			.markFailed(any(), eq(OrderReasons.CHECKOUT_INTERRUPTED));

		ResultActions result = checkout().andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("CHECKOUT_INTERRUPTED"))
			.andExpect(jsonPath("$.orderId").isNotEmpty());

		UUID orderId = orderIdOf(result);
		assertThat(releaseRequests()).hasSize(1);
		assertThat(paymentRequests()).isEmpty();

		// markFailed başarısız olduğu için markStockReleased çağrılmaz; sipariş pending+requested kalır (Adım 8 toplar).
		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("s", "pending").containsEntry("st", "requested");
		assertThat(row.get("f")).isNull();
		assertThat(outboxTypes(orderId)).isEmpty();

		assertCleanLogs(output, orderId);
	}

	// --- 2.b: Payment Initiated ama attachPayment başarısız ---

	@Test
	@DisplayName("2.b: Payment Initiated ama uk_orders_payment ihlali -> 201 pending, ERROR logu, release çağrılmaz")
	void attachPaymentFailsWithUniqueConstraint(CapturedOutput output) throws Exception {
		stubHappyPath();
		stubReleaseReleased();
		org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
				"Duplicate entry", new SQLException("Duplicate entry", "23000", 1062), "uk_orders_payment");
		doThrow(new DataIntegrityViolationException("uk_orders_payment", cve))
			.when(this.transactions)
			.attachPayment(any(), any());

		ResultActions result = checkout().andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("pending"))
			.andExpect(jsonPath("$.failureCode").isEmpty());

		UUID orderId = idOf(result);
		assertThat(releaseRequests()).isEmpty();

		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("s", "pending").containsEntry("st", "held");
		assertThat(row.get("p")).isNull();
		assertThat(outboxTypes(orderId)).isEmpty();

		assertThat(output).contains("ERROR").contains("Payment attachment violated unique constraint");
		assertCleanLogs(output, orderId);
	}

	@Test
	@DisplayName("2.b: Payment Initiated ama genel DB hatası ve sipariş okunabiliyor -> 201 pending, WARN logu")
	void attachPaymentFailsGeneralDbErrorOrderReadable(CapturedOutput output) throws Exception {
		stubHappyPath();
		stubReleaseReleased();
		doThrow(new CannotAcquireLockException("Deadlock")).when(this.transactions).attachPayment(any(), any());

		ResultActions result = checkout().andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("pending"))
			.andExpect(jsonPath("$.failureCode").isEmpty());

		UUID orderId = idOf(result);
		assertThat(releaseRequests()).isEmpty();

		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("s", "pending").containsEntry("st", "held");
		assertThat(outboxTypes(orderId)).isEmpty();
		assertThat(output).contains("WARN").contains("Payment attachment failed due to database error");
		assertCleanLogs(output, orderId);
	}

	@Test
	@DisplayName("2.b: Payment Initiated ama genel DB hatası ve sipariş okunamıyor -> 503 CHECKOUT_INTERRUPTED")
	void attachPaymentFailsGeneralDbErrorOrderNotReadable(CapturedOutput output) throws Exception {
		stubHappyPath();
		stubReleaseReleased();
		doThrow(new CannotAcquireLockException("DB dead")).when(this.transactions).attachPayment(any(), any());
		doThrow(new CannotAcquireLockException("DB dead")).when(this.transactions).findOwned(any(), any());

		ResultActions result = checkout().andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("CHECKOUT_INTERRUPTED"))
			.andExpect(jsonPath("$.orderId").isNotEmpty());

		UUID orderId = orderIdOf(result);
		assertThat(releaseRequests()).isEmpty();

		// DB'de sipariş failed yapılmadı (pending+held kaldı).
		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("s", "pending").containsEntry("st", "held");
		assertThat(outboxTypes(orderId)).isEmpty();

		assertThat(output).contains("WARN")
			.contains("Failed to read order after payment attachment failure; returning interrupted");
		assertCleanLogs(output, orderId);
	}

	// --- 2.c: Reserve başarısızlığından sonra markFailed başarısız ---

	@Test
	@DisplayName("2.c: Reserve Insufficient ama markFailed başarısız -> 409 INSUFFICIENT_STOCK, release çağrıldı, pending+requested")
	void reserveFailureFollowedByMarkFailedFailure(CapturedOutput output) throws Exception {
		stubCart(new Line(this.book, 1));
		stubLookup(this.book);
		stubReserve(stockProblem("INSUFFICIENT_STOCK", this.book.id()));
		stubReleaseReleased();
		doThrow(new CannotAcquireLockException("Lock timeout")).when(this.transactions)
			.markFailed(any(), eq(OrderReasons.OUT_OF_STOCK));

		ResultActions result = checkout().andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
			.andExpect(jsonPath("$.orderId").isNotEmpty());

		UUID orderId = orderIdOf(result);
		// markFailed başarısız olsa bile Catalog release çağrılır!
		assertThat(releaseRequests()).hasSize(1);

		// markFailed başarısız olduğu için sipariş DB'de pending+requested kalır (stok durumu güncellenmez).
		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("s", "pending").containsEntry("st", "requested");
		assertThat(row.get("f")).isNull();
		assertThat(outboxTypes(orderId)).isEmpty();

		assertThat(output).contains("WARN")
			.contains("Failed to mark order failed after reserve failure; attempting stock release");
		assertCleanLogs(output, orderId);
	}

	// --- 2.d: Payment NotPerformed/Rejected sonrası markFailed başarısız ---

	@Test
	@DisplayName("2.d: Payment NotPerformed sonrası markFailed başarısız -> 503 PAYMENT_UNAVAILABLE, release DENENMEZ")
	void paymentNotPerformedFollowedByMarkFailedFailure(CapturedOutput output) throws Exception {
		stubCart(new Line(this.book, 1));
		stubLookup(this.book);
		stubReserveHeld();
		PAYMENT.stop();
		stubReleaseReleased();
		doThrow(new CannotAcquireLockException("Lock timeout")).when(this.transactions)
			.markFailed(any(), eq(OrderReasons.PAYMENT_UNAVAILABLE));

		ResultActions result = checkout().andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("PAYMENT_UNAVAILABLE"))
			.andExpect(jsonPath("$.orderId").isNotEmpty());

		UUID orderId = orderIdOf(result);
		// Sipariş pending göründüğü için release DENENMEZ (Adım 8 uzlaştırması Payment'a sorar).
		assertThat(releaseRequests()).isEmpty();

		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("s", "pending").containsEntry("st", "held");
		assertThat(row.get("f")).isNull();
		assertThat(outboxTypes(orderId)).isEmpty();
		assertThat(outboxTypes(orderId)).isEmpty();

		assertThat(output).contains("WARN")
			.contains("Failed to mark order failed after payment failure; skipping stock release");
		assertCleanLogs(output, orderId);
	}

	@Test
	@DisplayName("2.d: Payment Rejected sonrası markFailed başarısız -> 503 PAYMENT_UNAVAILABLE, release DENENMEZ")
	void paymentRejectedFollowedByMarkFailedFailure(CapturedOutput output) throws Exception {
		stubCart(new Line(this.book, 1));
		stubLookup(this.book);
		stubReserveHeld();
		stubPayment(problem(409, "PAYMENT_ORDER_MISMATCH"));
		stubReleaseReleased();
		doThrow(new CannotAcquireLockException("Lock timeout")).when(this.transactions)
			.markFailed(any(), eq(OrderReasons.PAYMENT_REJECTED));

		ResultActions result = checkout().andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("PAYMENT_UNAVAILABLE"))
			.andExpect(jsonPath("$.orderId").isNotEmpty());

		UUID orderId = orderIdOf(result);
		// release DENENMEZ
		assertThat(releaseRequests()).isEmpty();

		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("s", "pending").containsEntry("st", "held");
		assertThat(row.get("f")).isNull();

		assertCleanLogs(output, orderId);
	}

	// --- 2.e: TX1 (insert) uk dışı bir DB hatası ---

	@Test
	@DisplayName("2.e: TX1 (insert) genel DB hatası -> 503 ORDER_UNAVAILABLE, hiçbir dış çağrı yok, satır yok")
	void insertGeneralDbFailure(CapturedOutput output) throws Exception {
		stubHappyPath();
		stubReleaseReleased();
		doThrow(new CannotAcquireLockException("Lock timeout")).when(this.transactions).insert(any());

		checkout().andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("ORDER_UNAVAILABLE"))
			.andExpect(jsonPath("$.orderId").doesNotExist());

		assertThat(orderCount()).isZero();
		assertThat(reserveRequests()).isEmpty();
		assertThat(paymentRequests()).isEmpty();
		assertThat(releaseRequests()).isEmpty();

		assertThat(output).contains("WARN").contains("Order insert failed due to database error");
		assertCleanLogs(output);
	}

	@Test
	@DisplayName("2.e: TX1 (insert) uk_orders_pending_user dışı kısıt hatası -> 503 ORDER_UNAVAILABLE")
	void insertUnexpectedConstraintFailure(CapturedOutput output) throws Exception {
		stubHappyPath();
		stubReleaseReleased();
		doThrow(new DataIntegrityViolationException("ck_orders_unknown",
				new SQLException("Check constraint violation", "23000", 3819)))
			.when(this.transactions)
			.insert(any());

		checkout().andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("ORDER_UNAVAILABLE"))
			.andExpect(jsonPath("$.orderId").doesNotExist());

		assertThat(orderCount()).isZero();
		assertThat(reserveRequests()).isEmpty();
		assertThat(paymentRequests()).isEmpty();
		assertThat(releaseRequests()).isEmpty();

		assertThat(output).contains("WARN")
			.contains("Order insert failed due to unexpected database constraint violation");
		assertCleanLogs(output);
	}

	private void assertCleanLogs(CapturedOutput output, UUID... orderIds) {
		String logs = output.getAll();
		assertThat(logs).doesNotContain(this.userId.toString())
			.doesNotContain(this.book.id().toString())
			.doesNotContain(this.book.title())
			.doesNotContain(RECIPIENT)
			.doesNotContain(PHONE)
			.doesNotContain(CITY);
		for (UUID orderId : orderIds) {
			// Sadece INFO/WARN/ERROR log satırlarında orderId aranır; WireMock çıktısı hariç.
			assertThat(logs).doesNotContain("id=" + orderId)
				.doesNotContain("orderId=" + orderId)
				.doesNotContain("orderId:" + orderId);
		}
	}

}
