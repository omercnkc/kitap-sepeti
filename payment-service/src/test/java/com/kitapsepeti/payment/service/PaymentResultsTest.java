package com.kitapsepeti.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.kitapsepeti.common.error.ResourceNotFoundException;
import com.kitapsepeti.common.outbox.OutboxService;
import com.kitapsepeti.payment.ApiTestSupport;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.entity.TransitionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Ödeme sonucu: durum geçişi + outbox satırı aynı transaction'da; yalnızca APPLIED olay üretir. */
@ExtendWith(OutputCaptureExtension.class)
class PaymentResultsTest extends ApiTestSupport {

	private static final String CONFLICT_WARNING = "Conflicting payment result ignored";

	@Autowired
	private PaymentResults results;

	@Autowired
	private Clock clock;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private Payment payment;

	@BeforeEach
	void createPayment() {
		payment = newPayment();
	}

	@Test
	void succeededAppliesTransitionAndWritesOneUnpublishedOutboxRow() {
		assertThat(results.recordSucceeded(payment.getId())).isEqualTo(TransitionResult.APPLIED);

		Map<String, Object> row = paymentRow(payment.getId());
		assertThat(row).containsEntry("status", "succeeded").containsEntry("failure_code", null);
		Map<String, Object> event = singleOutboxRow();
		assertThat(event).containsEntry("event_type", "PaymentSucceeded")
			.containsEntry("aggregate_type", "payment")
			.containsEntry("aggregate_id", payment.getId().toString())
			.containsEntry("published_at", null);

		Map<String, Object> payload = payload(event);
		assertThat(payload).containsOnlyKeys("eventVersion", "eventId", "paymentId", "orderId", "amount", "currency",
				"occurredAt");
		assertThat(payload).containsEntry("eventVersion", 1)
			.containsEntry("eventId", event.get("id"))
			.containsEntry("paymentId", payment.getId().toString())
			.containsEntry("orderId", payment.getOrderId().toString())
			.containsEntry("amount", "10.99")
			.containsEntry("currency", "TRY");
		assertThat(payload.get("amount")).isInstanceOf(String.class);
		assertThat(Instant.parse((String) payload.get("occurredAt")))
			.isEqualTo(((LocalDateTime) row.get("updated_at")).toInstant(ZoneOffset.UTC));
	}

	@Test
	void failedAppliesTransitionAndPaymentFailedCarriesFailureCode() {
		assertThat(results.recordFailed(payment.getId(), "CARD_DECLINED")).isEqualTo(TransitionResult.APPLIED);

		assertThat(paymentRow(payment.getId())).containsEntry("status", "failed")
			.containsEntry("failure_code", "CARD_DECLINED");
		Map<String, Object> event = singleOutboxRow();
		assertThat(event).containsEntry("event_type", "PaymentFailed").containsEntry("aggregate_type", "payment");
		Map<String, Object> payload = payload(event);
		assertThat(payload).containsOnlyKeys("eventVersion", "eventId", "paymentId", "orderId", "amount", "currency",
				"failureCode", "occurredAt");
		assertThat(payload).containsEntry("failureCode", "CARD_DECLINED")
			.containsEntry("eventId", event.get("id"))
			.containsEntry("amount", "10.99");
	}

	@Test
	void repeatedResultIsAlreadyInStateAndWritesNoSecondRow() {
		results.recordSucceeded(payment.getId());
		Map<String, Object> before = paymentRow(payment.getId());

		assertThat(results.recordSucceeded(payment.getId())).isEqualTo(TransitionResult.ALREADY_IN_STATE);

		assertThat(paymentRow(payment.getId())).isEqualTo(before);
		assertThat(outboxCount()).isEqualTo(1);

		Payment failed = newPayment();
		results.recordFailed(failed.getId(), "CARD_DECLINED");
		assertThat(results.recordFailed(failed.getId(), "CARD_DECLINED")).isEqualTo(TransitionResult.ALREADY_IN_STATE);
		assertThat(outboxCount()).isEqualTo(2);
	}

	@Test
	void conflictingResultChangesNothingAndWarnsWithoutValues(CapturedOutput output) {
		results.recordSucceeded(payment.getId());
		Map<String, Object> before = paymentRow(payment.getId());

		assertThat(results.recordFailed(payment.getId(), "INSUFFICIENT_FUNDS"))
			.isEqualTo(TransitionResult.CONFLICTING_FINAL);

		assertThat(paymentRow(payment.getId())).isEqualTo(before);
		assertThat(outboxCount()).isEqualTo(1);
		Payment failed = newPayment();
		results.recordFailed(failed.getId(), "CARD_DECLINED");
		assertThat(results.recordFailed(failed.getId(), "EXPIRED_CARD")).isEqualTo(TransitionResult.CONFLICTING_FINAL);
		assertThat(results.recordSucceeded(failed.getId())).isEqualTo(TransitionResult.CONFLICTING_FINAL);
		assertThat(paymentRow(failed.getId())).containsEntry("status", "failed")
			.containsEntry("failure_code", "CARD_DECLINED");
		assertThat(outboxCount()).isEqualTo(2);

		List<String> warnings = output.getAll().lines().filter(line -> line.contains(CONFLICT_WARNING)).toList();
		assertThat(warnings).hasSize(3).allSatisfy(line -> assertThat(line).contains(" WARN ")
			.endsWith(CONFLICT_WARNING));
		assertThat(output).doesNotContain("INSUFFICIENT_FUNDS").doesNotContain("EXPIRED_CARD");
	}

	@Test
	void outboxFailureRollsBackTheTransition() {
		Map<String, Object> before = paymentRow(payment.getId());
		// Stub transaction proxy'sinin arkasındaki spy'a kurulur; proxy üzerinden kurulsaydı MANDATORY kontrolü çalışırdı.
		OutboxService spy = AopTestUtils.getUltimateTargetObject(outbox);
		doThrow(new IllegalStateException("outbox write failed")).when(spy).append(any(), any(), any(), any());

		assertThatThrownBy(() -> results.recordSucceeded(payment.getId())).isInstanceOf(IllegalStateException.class)
			.hasMessage("outbox write failed");
		assertThatThrownBy(() -> results.recordFailed(payment.getId(), "CARD_DECLINED"))
			.isInstanceOf(IllegalStateException.class);

		assertThat(paymentRow(payment.getId())).isEqualTo(before).containsEntry("status", "initiated");
		assertThat(outboxCount()).isZero();
	}

	@Test
	void outerTransactionRollbackAlsoRollsBackTransitionAndEvent() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			assertThat(results.recordSucceeded(payment.getId())).isEqualTo(TransitionResult.APPLIED);
			status.setRollbackOnly();
		});

		assertThat(paymentRow(payment.getId())).containsEntry("status", "initiated");
		assertThat(outboxCount()).isZero();
	}

	@Test
	void outboxAppendRequiresAnOpenTransaction() {
		assertThatThrownBy(() -> outbox.append("payment", payment.getId(), "PaymentSucceeded", id -> Map.of()))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(outboxCount()).isZero();
	}

	@Test
	void unknownPaymentIsNotFoundAndWritesNothing() {
		assertThatThrownBy(() -> results.recordSucceeded(UUID.randomUUID()))
			.isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> results.recordFailed(UUID.randomUUID(), "CARD_DECLINED"))
			.isInstanceOf(ResourceNotFoundException.class);
		assertThat(outboxCount()).isZero();
	}

	@Test
	void invalidFailureCodeChangesNothing() {
		assertThatIllegalArgumentException().isThrownBy(() -> results.recordFailed(payment.getId(), "card declined"));
		assertThat(paymentRow(payment.getId())).containsEntry("status", "initiated");
		assertThat(outboxCount()).isZero();
	}

	/** FOR UPDATE ikisini sıraya sokar: biri APPLIED, diğeri onun sonucunu görüp CONFLICTING_FINAL; tek olay. */
	@Test
	void concurrentSucceededAndFailedApplyExactlyOne() throws Exception {
		for (int round = 0; round < 5; round++) {
			Payment contested = newPayment();
			List<Object> outcomes = runConcurrently(List.of(
					() -> results.recordSucceeded(contested.getId()),
					() -> results.recordFailed(contested.getId(), "CARD_DECLINED")));

			assertThat(outcomes).containsExactlyInAnyOrder(TransitionResult.APPLIED, TransitionResult.CONFLICTING_FINAL);
			String status = (String) paymentRow(contested.getId()).get("status");
			List<String> types = jdbc.queryForList(
					"SELECT event_type FROM outbox WHERE aggregate_id = UUID_TO_BIN(?)", String.class,
					contested.getId().toString());
			assertThat(types).containsExactly(status.equals("succeeded") ? "PaymentSucceeded" : "PaymentFailed");
		}
	}

	@Test
	void logsContainNoIdsAmountFailureCodeOrPayload(CapturedOutput output) {
		results.recordSucceeded(payment.getId());
		results.recordSucceeded(payment.getId());
		results.recordFailed(payment.getId(), "CARD_DECLINED");
		Payment failed = newPayment();
		results.recordFailed(failed.getId(), "CARD_DECLINED");
		String eventId = (String) jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM outbox", String.class).get(0);

		for (String value : List.of(payment.getId().toString(), payment.getOrderId().toString(),
				failed.getId().toString(), failed.getOrderId().toString(), eventId, "10.99", "CARD_DECLINED",
				"eventVersion", "PaymentSucceeded", "PaymentFailed")) {
			assertThat(output).doesNotContain(value);
		}
	}

	// --- yardımcılar ---

	private Payment newPayment() {
		return payments.saveAndFlush(Payment.initiate(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.99"),
				"TRY", PaymentProviderType.MOCK, clock));
	}

	private Map<String, Object> paymentRow(UUID paymentId) {
		return jdbc.queryForMap("SELECT status, failure_code, updated_at FROM payments WHERE id = UUID_TO_BIN(?)",
				paymentId.toString());
	}

	private Map<String, Object> singleOutboxRow() {
		assertThat(outboxCount()).isEqualTo(1);
		return jdbc.queryForMap("""
				SELECT BIN_TO_UUID(id) AS id, aggregate_type, BIN_TO_UUID(aggregate_id) AS aggregate_id, event_type,
					payload, published_at
				FROM outbox""");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> payload(Map<String, Object> outboxRow) {
		return jsonMapper.readValue((String) outboxRow.get("payload"), Map.class);
	}

	private int outboxCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class);
	}

	/** Görevler ayrı thread'lerde; hepsi hazır olunca tek latch ile aynı anda başlatılır. Sonuçlar görev sırasıyla. */
	private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		try {
			CountDownLatch ready = new CountDownLatch(tasks.size());
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return task.call();
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
