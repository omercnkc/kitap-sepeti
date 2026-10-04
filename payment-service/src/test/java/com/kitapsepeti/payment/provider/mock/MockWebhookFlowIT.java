package com.kitapsepeti.payment.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.exception.PaymentErrorCode;
import com.kitapsepeti.payment.exception.WebhookRejectedException;
import com.kitapsepeti.payment.provider.mock.MockWebhookDispatcher.Delivery;
import com.kitapsepeti.payment.support.InternalTestKeys;
import com.kitapsepeti.payment.support.WebhookTestSecrets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Uçtan uca: ödeme oluşturma → commit sonrası gecikmeli mock webhook (gerçek HTTP, kendi portuna) → sonuç →
 * outbox → RabbitMQ. Gönderici hata yolları gerçek webhook ucuna karşı.
 */
@ExtendWith(OutputCaptureExtension.class)
class MockWebhookFlowIT extends MockFlowTestSupport {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Test
	void succeededPaymentIsCompletedByTheMockWebhookAndPublished() {
		String queue = bindTemporaryQueue("payment.#");

		ApiResponse created = create("10.00");

		assertThat(created.status()).isEqualTo(201);
		assertThat(created.field("status")).isEqualTo("initiated");
		UUID paymentId = created.paymentId();
		await().atMost(TIMEOUT).until(() -> "succeeded".equals(status(paymentId)));
		assertThat(get(paymentId).field("status")).isEqualTo("succeeded");
		assertThat(providerEventIds()).containsExactly("mock_evt_" + paymentId);
		String outboxId = jdbc.queryForObject(
				"SELECT BIN_TO_UUID(id) FROM outbox WHERE aggregate_id = UUID_TO_BIN(?) AND event_type = 'PaymentSucceeded'",
				String.class, paymentId.toString());
		assertThat(outboxCount()).isEqualTo(1);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(1));
		Message message = rabbitTemplate.receive(queue);
		MessageProperties properties = message.getMessageProperties();
		assertThat(properties.getMessageId()).isEqualTo(outboxId);
		assertThat(properties.getType()).isEqualTo("PaymentSucceeded");
		assertThat(properties.getReceivedRoutingKey()).isEqualTo("payment.succeeded");
		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		assertThat((String) JsonPath.read(body, "$.eventId")).isEqualTo(outboxId);
		assertThat((String) JsonPath.read(body, "$.paymentId")).isEqualTo(paymentId.toString());
		assertThat((String) JsonPath.read(body, "$.amount")).isEqualTo("10.00");
		await().atMost(TIMEOUT).until(() -> jdbc.queryForObject(
				"SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class) == 0);
	}

	@Test
	void failCentsPaymentIsFailedWithCardDeclinedAndPublished() {
		String queue = bindTemporaryQueue("payment.#");

		ApiResponse created = create("10.99");

		assertThat(created.status()).isEqualTo(201);
		UUID paymentId = created.paymentId();
		await().atMost(TIMEOUT).until(() -> "failed".equals(status(paymentId)));
		ApiResponse fetched = get(paymentId);
		assertThat(fetched.field("failureCode")).isEqualTo("CARD_DECLINED");
		assertThat(providerEventIds()).containsExactly("mock_evt_" + paymentId);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(1));
		Message message = rabbitTemplate.receive(queue);
		assertThat(message.getMessageProperties().getType()).isEqualTo("PaymentFailed");
		assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("payment.failed");
		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		assertThat((String) JsonPath.read(body, "$.paymentId")).isEqualTo(paymentId.toString());
		assertThat((String) JsonPath.read(body, "$.failureCode")).isEqualTo("CARD_DECLINED");
	}

	@Test
	void createRespondsBeforeTheWebhookIsSent() {
		create("1.00");
		await().atMost(TIMEOUT).until(() -> dispatcher.pendingCount() == 0);
		long started = System.nanoTime();

		ApiResponse created = create("10.00");

		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
		assertThat(created.status()).isEqualTo(201);
		assertThat(created.field("status")).isEqualTo("initiated");
		assertThat(elapsed).isLessThan(DELAY);
		assertThat(status(created.paymentId())).isEqualTo("initiated");
		await().atMost(TIMEOUT).until(() -> "succeeded".equals(status(created.paymentId())));
	}

	@Test
	void repeatedCreateForTheSameOrderDispatchesNoSecondWebhook() throws InterruptedException {
		UUID orderId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		ApiResponse first = create(orderId, userId, "10.00");
		ApiResponse beforeWebhook = create(orderId, userId, "10.00");

		assertThat(first.status()).isEqualTo(201);
		assertThat(beforeWebhook.status()).isEqualTo(200);
		assertThat(dispatcher.pendingCount()).isEqualTo(1);
		UUID paymentId = first.paymentId();
		await().atMost(TIMEOUT).until(() -> "succeeded".equals(status(paymentId)));

		ApiResponse afterWebhook = create(orderId, userId, "10.00");
		Thread.sleep(DELAY.plusMillis(500).toMillis());

		assertThat(afterWebhook.status()).isEqualTo(200);
		assertThat(afterWebhook.field("status")).isEqualTo("succeeded");
		assertThat(dispatcher.pendingCount()).isZero();
		verify(webhookSpy(), times(1)).handle(any(), any());
		assertThat(providerEventIds()).hasSize(1);
		assertThat(outboxCount()).isEqualTo(1);
	}

	@Test
	void serverErrorFromWebhookLeavesPaymentInitiated(CapturedOutput output) {
		Payment payment = newPayment("10.00");
		doThrow(new IllegalStateException("simulated")).when(webhookSpy()).handle(any(), any());

		Delivery delivery = dispatcher.send(payment.getId());

		assertThat(delivery).isEqualTo(Delivery.FAILED);
		assertThat(status(payment.getId())).isEqualTo("initiated");
		assertThat(providerEventIds()).isEmpty();
		assertThat(output).contains("Mock webhook delivery failed (status=500)");
		assertThat(output).doesNotContain(payment.getId().toString()).doesNotContain(payment.getProviderPaymentId());
	}

	@Test
	void rejectedWebhookIsNotRetried(CapturedOutput output) throws InterruptedException {
		Payment payment = newPayment("10.00");
		doThrow(new WebhookRejectedException(PaymentErrorCode.UNKNOWN_PAYMENT)).when(webhookSpy())
			.handle(any(), any());

		Delivery delivery = dispatcher.send(payment.getId());
		Thread.sleep(500);

		assertThat(delivery).isEqualTo(Delivery.REJECTED);
		verify(webhookSpy(), times(1)).handle(any(), any());
		assertThat(status(payment.getId())).isEqualTo("initiated");
		assertThat(output).contains("Mock webhook rejected (status=400)");
		assertThat(output).doesNotContain(payment.getId().toString()).doesNotContain(payment.getProviderPaymentId());
	}

	@Test
	void paymentFinalizedBeforeSendTimeIsNotSent() {
		Payment payment = newPayment("10.00");
		results.recordSucceeded(payment.getId());

		Delivery delivery = dispatcher.send(payment.getId());

		assertThat(delivery).isEqualTo(Delivery.SKIPPED);
		verify(webhookSpy(), never()).handle(any(), any());
		assertThat(providerEventIds()).isEmpty();
	}

	@Test
	void flowLogsContainNoIdsAmountsSignatureOrSecrets(CapturedOutput output) {
		UUID orderId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID succeeded = create(orderId, userId, "7654.00").paymentId();
		UUID failed = create("7654.99").paymentId();
		await().atMost(TIMEOUT).until(() -> "succeeded".equals(status(succeeded)) && "failed".equals(status(failed)));
		Payment stale = newPaymentAt(clock.instant().minusSeconds(60), "7654.50");
		clock.useSystemTime();

		assertThat(recoveryJob.resendStale()).isEqualTo(1);

		await().atMost(TIMEOUT).until(() -> jdbc.queryForObject(
				"SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class) == 0);
		List<String> references = jdbc.queryForList("SELECT provider_payment_id FROM payments", String.class);
		assertThat(references).hasSize(3);
		for (String value : List.of(succeeded.toString(), failed.toString(), stale.getId().toString(),
				orderId.toString(), userId.toString(), "7654.", "mock_evt_", "sha256=", WebhookTestSecrets.MOCK_SECRET,
				InternalTestKeys.ORDER_SERVICE_KEY)) {
			assertThat(output).doesNotContain(value);
		}
		references.forEach(reference -> assertThat(output).doesNotContain(reference));
		assertThat(output).contains("Mock recovery resent 1 webhook(s)");
		assertThat(output).doesNotContain(" WARN ").doesNotContain(" ERROR ");
	}

}
