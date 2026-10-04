package com.kitapsepeti.payment.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.payment.ApiTestSupport;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.service.PaymentResults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Worker açıkken (yalnızca relay testlerinde) outbox → RabbitMQ akışı. Her test exchange'e kendi desenine
 * bağlı geçici bir kuyruk açar; yayıncı, gerçek yayıncının etrafında hata enjekte edebilen bir test double'ıdır.
 */
@TestPropertySource(properties = { "app.outbox.enabled=true", "app.outbox.poll-interval=200ms" })
@Import(FaultInjectingPublisher.Config.class)
@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayIT extends ApiTestSupport {

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private TopicExchange eventsExchange;

	@Autowired
	private FaultInjectingPublisher publisher;

	@Autowired
	private PaymentResults results;

	@Autowired
	private Clock clock;

	private final List<String> queues = new ArrayList<>();

	@BeforeEach
	void resetPublisher() {
		publisher.reset();
	}

	@AfterEach
	void deleteTemporaryQueues() {
		publisher.reset();
		queues.forEach(amqpAdmin::deleteQueue);
	}

	@Test
	void succeededPaymentEmitsPaymentSucceededWithContractPropertiesAndRowMarkedPublished() {
		String queue = bindTemporaryQueue("payment.#");
		Payment payment = newPayment("10.99");
		results.recordSucceeded(payment.getId());
		String outboxId = outboxIdOf(payment, "PaymentSucceeded");
		String payload = jdbc.queryForObject("SELECT payload FROM outbox WHERE id = UUID_TO_BIN(?)", String.class,
				outboxId);
		LocalDateTime createdAt = jdbc.queryForObject("SELECT created_at FROM outbox WHERE id = UUID_TO_BIN(?)",
				LocalDateTime.class, outboxId);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(1));
		Message message = rabbitTemplate.receive(queue);

		assertThat(message).isNotNull();
		MessageProperties properties = message.getMessageProperties();
		assertThat(properties.getMessageId()).isEqualTo(outboxId);
		assertThat(properties.getType()).isEqualTo("PaymentSucceeded");
		assertThat(properties.getContentType()).isEqualTo("application/json");
		assertThat(properties.getContentEncoding()).isEqualTo("UTF-8");
		assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		assertThat(properties.getReceivedExchange()).isEqualTo("kitapsepeti.events");
		assertThat(properties.getReceivedRoutingKey()).isEqualTo("payment.succeeded");
		assertThat(properties.getTimestamp().toInstant())
			.isEqualTo(createdAt.toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
		assertThat(properties.<Object>getHeader(OutboxPublisher.AGGREGATE_TYPE_HEADER)).hasToString("payment");
		assertThat(properties.<Object>getHeader(OutboxPublisher.AGGREGATE_ID_HEADER))
			.hasToString(payment.getId().toString());
		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		assertThat(body).isEqualTo(payload).contains("\"eventId\": \"" + outboxId + "\"")
			.contains("\"amount\": \"10.99\"");

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(publishedAt(outboxId)).isNotNull());
		assertThat(rabbitTemplate.receive(queue)).isNull();
	}

	@Test
	void failedPaymentEmitsPaymentFailedOnPaymentFailedRoutingKey() {
		String failedOnly = bindTemporaryQueue("payment.failed");
		Payment payment = newPayment("20.99");
		results.recordFailed(payment.getId(), "CARD_DECLINED");
		String outboxId = outboxIdOf(payment, "PaymentFailed");

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(failedOnly)).isEqualTo(1));
		Message message = rabbitTemplate.receive(failedOnly);

		MessageProperties properties = message.getMessageProperties();
		assertThat(properties.getMessageId()).isEqualTo(outboxId);
		assertThat(properties.getType()).isEqualTo("PaymentFailed");
		assertThat(properties.getReceivedRoutingKey()).isEqualTo("payment.failed");
		assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
			.contains("\"failureCode\": \"CARD_DECLINED\"");
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(publishedAt(outboxId)).isNotNull());
	}

	@Test
	void rowsArePublishedInCreatedAtOrder() {
		String queue = bindTemporaryQueue("payment.#");
		List<UUID> ids = OutboxTestRows.newIds(3);
		OutboxTestRows.insert(jdbc, ids);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(3));
		List<String> received = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			received.add(rabbitTemplate.receive(queue).getMessageProperties().getMessageId());
		}
		assertThat(received).containsExactly(ids.get(0).toString(), ids.get(1).toString(), ids.get(2).toString());

		Payment first = newPayment("1.00");
		Payment second = newPayment("2.00");
		results.recordSucceeded(first.getId());
		results.recordFailed(second.getId(), "CARD_DECLINED");
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(2));
		List<String> types = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			types.add(rabbitTemplate.receive(queue).getMessageProperties().getType());
		}
		assertThat(types).containsExactly("PaymentSucceeded", "PaymentFailed");
	}

	@Test
	void failureStopsTheBatchKeepingOrderAndCommitsEarlierRows() {
		String queue = bindTemporaryQueue("payment.#");
		List<UUID> ids = OutboxTestRows.newIds(3);
		// Hata INSERT'ten önce kurulur; worker satırları eklendiği anda görebilir.
		publisher.failOn(ids.get(1)::equals);
		OutboxTestRows.insert(jdbc, ids);
		String first = ids.get(0).toString();
		String second = ids.get(1).toString();
		String third = ids.get(2).toString();

		await().atMost(TIMEOUT).until(() -> publisher.attemptsFor(second) >= 2);
		assertThat(publishedAt(first)).isNotNull();
		assertThat(publishedAt(second)).isNull();
		assertThat(publishedAt(third)).isNull();
		assertThat(publisher.attemptsFor(third)).isZero();

		publisher.failOn(id -> false);

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(publishedAt(second)).isNotNull();
			assertThat(publishedAt(third)).isNotNull();
		});
		List<String> received = new ArrayList<>();
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(3));
		for (int i = 0; i < 3; i++) {
			received.add(rabbitTemplate.receive(queue).getMessageProperties().getMessageId());
		}
		assertThat(received).containsExactly(first, second, third);
	}

	@Test
	void publishingLogsNoPayloadIdsAmountOrFailureCode(CapturedOutput output) {
		String queue = bindTemporaryQueue("payment.#");
		Payment succeeded = newPayment("7654.32");
		Payment failed = newPayment("7654.33");
		results.recordSucceeded(succeeded.getId());
		results.recordFailed(failed.getId(), "CARD_DECLINED");

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(2));
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class)).isZero());

		for (String value : List.of(succeeded.getId().toString(), succeeded.getOrderId().toString(),
				failed.getId().toString(), failed.getOrderId().toString(), "7654.32", "7654.33", "CARD_DECLINED",
				"eventVersion", "\"amount\"")) {
			assertThat(output).doesNotContain(value);
		}
		assertThat(output).doesNotContain(" WARN ").doesNotContain(" ERROR ");
	}

	private Payment newPayment(String amount) {
		return payments.saveAndFlush(Payment.initiate(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(amount),
				"TRY", PaymentProviderType.MOCK, clock));
	}

	private String outboxIdOf(Payment payment, String eventType) {
		return jdbc.queryForObject(
				"SELECT BIN_TO_UUID(id) FROM outbox WHERE aggregate_id = UUID_TO_BIN(?) AND event_type = ?",
				String.class, payment.getId().toString(), eventType);
	}

	private String bindTemporaryQueue(String pattern) {
		Queue temporary = new AnonymousQueue();
		String name = amqpAdmin.declareQueue(temporary);
		queues.add(name);
		amqpAdmin.declareBinding(BindingBuilder.bind(temporary).to(eventsExchange).with(pattern));
		return name;
	}

	private long messageCount(String queue) {
		QueueInformation info = amqpAdmin.getQueueInfo(queue);
		return (info == null) ? -1 : info.getMessageCount();
	}

	private Object publishedAt(String outboxId) {
		return jdbc.queryForObject("SELECT published_at FROM outbox WHERE id = UUID_TO_BIN(?)", Object.class,
				outboxId);
	}

}
