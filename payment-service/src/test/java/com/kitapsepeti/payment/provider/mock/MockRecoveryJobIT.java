package com.kitapsepeti.payment.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.kitapsepeti.payment.dto.webhook.WebhookEvent;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Kurtarma görevi, elle tetiklenen turlarla (bağlamdaki görevin kendi turu 1 saatte bir). min-age 10 sn,
 * batch-size 3 ({@link MockFlowTestSupport}).
 */
@ExtendWith(OutputCaptureExtension.class)
class MockRecoveryJobIT extends MockFlowTestSupport {

	private final Instant t0 = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

	@Test
	void onlyOldInitiatedMockPaymentsWithReferenceAreResent(CapturedOutput output) {
		Payment old = newPaymentAt(t0, "10.00");
		Payment oldFinal = newPaymentAt(t0, "10.00");
		results.recordSucceeded(oldFinal.getId());
		clock.fixAt(t0);
		Payment oldWithoutReference = newPayment("10.00", false, PaymentProviderType.MOCK);
		Payment oldOtherProvider = newPayment("10.00", true, PaymentProviderType.IYZICO);
		Payment fresh = newPaymentAt(t0.plusSeconds(5), "10.00");
		clock.fixAt(t0.plusSeconds(12));

		assertThat(recoveryJob.resendStale()).isEqualTo(1);

		assertThat(status(old.getId())).isEqualTo("succeeded");
		assertThat(providerEventIds()).containsExactly("mock_evt_" + old.getId());
		assertThat(status(oldFinal.getId())).isEqualTo("succeeded");
		assertThat(status(oldWithoutReference.getId())).isEqualTo("initiated");
		assertThat(status(oldOtherProvider.getId())).isEqualTo("initiated");
		assertThat(status(fresh.getId())).isEqualTo("initiated");
		verify(webhookSpy(), times(1)).handle(any(), any());
		assertThat(output).contains("Mock recovery resent 1 webhook(s)");
	}

	@Test
	void batchIsLimitedAndSentInCreatedAtOrder() {
		Payment fifth = newPaymentAt(t0.plusSeconds(4), "10.00");
		Payment second = newPaymentAt(t0.plusSeconds(1), "10.00");
		Payment fourth = newPaymentAt(t0.plusSeconds(3), "10.00");
		Payment first = newPaymentAt(t0, "10.00");
		Payment third = newPaymentAt(t0.plusSeconds(2), "10.00");
		clock.fixAt(t0.plusSeconds(60));

		assertThat(recoveryJob.resendStale()).isEqualTo(3);

		ArgumentCaptor<WebhookEvent> sent = ArgumentCaptor.forClass(WebhookEvent.class);
		verify(webhookSpy(), times(3)).handle(eq(PaymentProviderType.MOCK), sent.capture());
		assertThat(sent.getAllValues()).extracting(WebhookEvent::providerPaymentId)
			.containsExactly(first.getProviderPaymentId(), second.getProviderPaymentId(),
					third.getProviderPaymentId());
		assertThat(status(fourth.getId())).isEqualTo("initiated");
		assertThat(status(fifth.getId())).isEqualTo("initiated");

		assertThat(recoveryJob.resendStale()).isEqualTo(2);
		assertThat(status(fourth.getId())).isEqualTo("succeeded");
		assertThat(status(fifth.getId())).isEqualTo("succeeded");
		assertThat(recoveryJob.resendStale()).isZero();
	}

	@Test
	void resendAfterSuccessfulDeliveryIsAHarmlessDuplicate() {
		Payment snapshot = newPaymentAt(t0, "10.00");
		clock.fixAt(t0.plusSeconds(60));
		assertThat(dispatcher.send(snapshot.getId())).isEqualTo(MockWebhookDispatcher.Delivery.DELIVERED);
		assertThat(status(snapshot.getId())).isEqualTo("succeeded");
		// Yarış: tur ödemeyi gönderimden önce initiated olarak okumuştu.
		doReturn(List.of(snapshot.getId())).when(payments).findStaleWithReference(any(), any(), any(), any());
		doReturn(Optional.of(snapshot)).when(payments).findById(snapshot.getId());

		assertThat(recoveryJob.resendStale()).isEqualTo(1);

		verify(webhookSpy(), times(2)).handle(any(), any());
		assertThat(providerEventIds()).containsExactly("mock_evt_" + snapshot.getId());
		assertThat(outboxCount()).isEqualTo(1);
		assertThat(status(snapshot.getId())).isEqualTo("succeeded");
	}

	@Test
	void failureForOnePaymentDoesNotStopTheOthers(CapturedOutput output) {
		Payment first = newPaymentAt(t0, "10.00");
		Payment broken = newPaymentAt(t0.plusSeconds(1), "10.00");
		Payment third = newPaymentAt(t0.plusSeconds(2), "10.99");
		doAnswer(invocation -> {
			WebhookEvent event = invocation.getArgument(1);
			if (event.providerPaymentId().equals(broken.getProviderPaymentId())) {
				throw new IllegalStateException("simulated");
			}
			return invocation.callRealMethod();
		}).when(webhookSpy()).handle(any(), any());
		clock.fixAt(t0.plusSeconds(60));

		assertThat(recoveryJob.resendStale()).isEqualTo(2);

		assertThat(status(first.getId())).isEqualTo("succeeded");
		assertThat(status(broken.getId())).isEqualTo("initiated");
		assertThat(paymentRow(third.getId())).containsEntry("status", "failed")
			.containsEntry("failure_code", "CARD_DECLINED");
		assertThat(output.toString().split("Mock webhook delivery failed \\(status=500\\)", -1)).hasSize(2);
		assertThat(output).contains("Mock recovery resent 2 webhook(s)");
		assertThat(output).doesNotContain(broken.getId().toString()).doesNotContain(broken.getProviderPaymentId());
	}

	@Test
	void nothingToResendLogsNothing(CapturedOutput output) {
		newPaymentAt(t0, "10.00");
		clock.fixAt(t0.plusSeconds(5));

		assertThat(recoveryJob.resendStale()).isZero();

		assertThat(output).doesNotContain("Mock recovery");
	}

	/**
	 * Görevin sorgusunun planı: {@code ix_payments_status_created} üzerinde aralık taraması, sıra indeksten (filesort
	 * yok). Hibernate'in ürettiği SQL ile aynı biçim.
	 */
	@Test
	void recoveryQueryUsesStatusCreatedIndexWithoutFilesort() {
		Instant base = t0.minus(1, ChronoUnit.DAYS);
		for (int i = 0; i < 300; i++) {
			String status = (i % 10 == 0) ? "initiated" : "succeeded";
			jdbc.update("""
					INSERT INTO payments (id, order_id, user_id, provider, provider_payment_id, amount, currency, status,
						created_at, updated_at)
					VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(UUID()), UUID_TO_BIN(UUID()), 'mock', CONCAT('mock_', UUID()),
						10.00, 'TRY', ?, ?, ?)""", status, Timestamp.from(base.plusSeconds(i)),
					Timestamp.from(base.plusSeconds(i)));
		}
		jdbc.execute("ANALYZE TABLE payments");

		Map<String, Object> plan = jdbc.queryForMap("""
				EXPLAIN SELECT p1_0.id FROM payments p1_0
				WHERE p1_0.status = 'initiated' AND p1_0.created_at < ? AND p1_0.provider = 'mock'
					AND p1_0.provider_payment_id IS NOT NULL
				ORDER BY p1_0.created_at, p1_0.id LIMIT 50""", Timestamp.from(t0));

		assertThat(plan.get("key")).isEqualTo("ix_payments_status_created");
		assertThat(plan.get("type")).isEqualTo("range");
		assertThat(String.valueOf(plan.get("Extra"))).doesNotContain("filesort");
		assertThat(recoveryJob.resendStale()).isEqualTo(3);
	}

}
