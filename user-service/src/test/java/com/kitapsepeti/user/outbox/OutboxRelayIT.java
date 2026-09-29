package com.kitapsepeti.user.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import com.kitapsepeti.user.ApiTestSupport;
import com.kitapsepeti.user.entity.OutboxEvent;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * Worker açıkken (yalnızca bu sınıfta) outbox → RabbitMQ akışı. Test, exchange'e {@code user.#} ile bağlı
 * geçici bir kuyruk açar; yayıncı, gerçek yayıncının etrafında hata enjekte edebilen bir test double'ıdır.
 */
@TestPropertySource(properties = { "app.outbox.enabled=true", "app.outbox.poll-interval=200ms" })
@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayIT extends ApiTestSupport {

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private static final String EMAIL = "relay@example.com";

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private TopicExchange eventsExchange;

	@Autowired
	private FaultInjectingPublisher publisher;

	private String queue;

	@BeforeEach
	void bindTemporaryQueue() {
		publisher.reset();
		Queue temporary = new AnonymousQueue();
		queue = amqpAdmin.declareQueue(temporary);
		amqpAdmin.declareBinding(BindingBuilder.bind(temporary).to(eventsExchange).with("user.#"));
	}

	@AfterEach
	void deleteTemporaryQueue() {
		publisher.reset();
		amqpAdmin.deleteQueue(queue);
	}

	@Test
	void registrationEventIsPublishedWithContractPropertiesAndRowMarkedPublished() throws Exception {
		registerAndGetAccessToken(EMAIL);
		String userId = userIdOf(EMAIL);
		String outboxId = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM outbox WHERE aggregate_id = UUID_TO_BIN(?)",
				String.class, userId);
		String payload = jdbc.queryForObject("SELECT payload FROM outbox WHERE id = UUID_TO_BIN(?)", String.class,
				outboxId);
		LocalDateTime createdAt = jdbc.queryForObject("SELECT created_at FROM outbox WHERE id = UUID_TO_BIN(?)",
				LocalDateTime.class, outboxId);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount()).isEqualTo(1));
		Message message = rabbitTemplate.receive(queue);

		assertThat(message).isNotNull();
		MessageProperties properties = message.getMessageProperties();
		assertThat(properties.getMessageId()).isEqualTo(outboxId);
		assertThat(properties.getType()).isEqualTo("UserRegistered");
		assertThat(properties.getContentType()).isEqualTo("application/json");
		assertThat(properties.getContentEncoding()).isEqualTo("UTF-8");
		assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		assertThat(properties.getReceivedRoutingKey()).isEqualTo("user.registered");
		assertThat(properties.getTimestamp().toInstant())
			.isEqualTo(createdAt.toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
		assertThat(properties.<Object>getHeader(OutboxPublisher.AGGREGATE_TYPE_HEADER)).hasToString("user");
		assertThat(properties.<Object>getHeader(OutboxPublisher.AGGREGATE_ID_HEADER)).hasToString(userId);
		assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(publishedAt(outboxId)).isNotNull());
		assertThat(rabbitTemplate.receive(queue)).isNull();
	}

	@Test
	void rowsStayUnpublishedWhileBrokerFailsAndArePublishedAfterRecovery(CapturedOutput output) throws Exception {
		publisher.failNextCalls(Integer.MAX_VALUE);

		registerAndGetAccessToken(EMAIL);
		loginAndGetAccessToken(EMAIL);
		String outboxId = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM outbox", String.class);

		await().atMost(TIMEOUT).until(() -> publisher.attemptsFor(outboxId) >= 3);
		assertThat(publishedAt(outboxId)).isNull();
		assertThat(messageCount()).isZero();
		assertThat(output).contains("Outbox publish failed, will retry on next poll: id=" + outboxId
				+ ", eventType=UserRegistered");
		assertThat(output).doesNotContain(EMAIL);

		publisher.failNextCalls(0);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(publishedAt(outboxId)).isNotNull());
		assertThat(messageCount()).isEqualTo(1);
		assertThat(rabbitTemplate.receive(queue).getMessageProperties().getMessageId()).isEqualTo(outboxId);
	}

	@Test
	void failureStopsTheBatchKeepingOrderAndCommitsEarlierRows() {
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
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount()).isEqualTo(3));
		for (int i = 0; i < 3; i++) {
			received.add(rabbitTemplate.receive(queue).getMessageProperties().getMessageId());
		}
		assertThat(received).containsExactly(first, second, third);
	}

	private long messageCount() {
		QueueInformation info = amqpAdmin.getQueueInfo(queue);
		return (info == null) ? -1 : info.getMessageCount();
	}

	private Object publishedAt(String outboxId) {
		return jdbc.queryForObject("SELECT published_at FROM outbox WHERE id = UUID_TO_BIN(?)", Object.class,
				outboxId);
	}

	/** Gerçek yayıncıyı sarar; istenen çağrılarda broker hatası gibi exception fırlatır. Worker thread'inden çağrılır. */
	static class FaultInjectingPublisher extends OutboxPublisher {

		private final List<UUID> attempts = new CopyOnWriteArrayList<>();

		private volatile int failuresRemaining;

		private volatile Predicate<UUID> failOn = id -> false;

		FaultInjectingPublisher(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
			super(rabbitTemplate, properties);
		}

		@Override
		public void publish(OutboxEvent event) {
			attempts.add(event.getId());
			if (failOn.test(event.getId()) || consumeFailure()) {
				throw new OutboxPublishException("Simulated broker failure");
			}
			super.publish(event);
		}

		private synchronized boolean consumeFailure() {
			if (failuresRemaining > 0) {
				failuresRemaining--;
				return true;
			}
			return false;
		}

		synchronized void failNextCalls(int count) {
			failuresRemaining = count;
		}

		void failOn(Predicate<UUID> predicate) {
			failOn = predicate;
		}

		long attemptsFor(String id) {
			UUID uuid = UUID.fromString(id);
			return attempts.stream().filter(uuid::equals).count();
		}

		synchronized void reset() {
			failuresRemaining = 0;
			failOn = id -> false;
			attempts.clear();
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FaultInjectionConfig {

		@Bean
		@Primary
		FaultInjectingPublisher faultInjectingPublisher(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
			return new FaultInjectingPublisher(rabbitTemplate, properties);
		}

	}

}
