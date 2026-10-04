package com.kitapsepeti.catalog.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.catalog.TestcontainersConfiguration;
import com.kitapsepeti.catalog.support.JwksServer;
import com.kitapsepeti.common.outbox.OutboxPublishException;
import com.kitapsepeti.common.outbox.OutboxRelay;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Gerçek broker kesintisi. Broker sabit bir host portunda koşar ki durdurulup yeniden açıldığında uygulamanın
 * bağlantı ayarı geçerli kalsın; bu yüzden paylaşılan RabbitMQ konteyneri yerine kendi konteynerini yönetir.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, FaultInjectingPublisher.Config.class })
@TestPropertySource(properties = { "app.outbox.enabled=true", "app.outbox.poll-interval=200ms" })
@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayBrokerOutageIT {

	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private static final int AMQP_PORT = JwksServer.freePort();

	private static RabbitMQContainer broker = startBroker();

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private TopicExchange eventsExchange;

	@Autowired
	private FaultInjectingPublisher publisher;

	@DynamicPropertySource
	static void rabbitProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.rabbitmq.host", broker::getHost);
		registry.add("spring.rabbitmq.port", () -> AMQP_PORT);
		registry.add("spring.rabbitmq.username", broker::getAdminUsername);
		registry.add("spring.rabbitmq.password", broker::getAdminPassword);
	}

	@AfterAll
	static void stopBroker() {
		broker.stop();
	}

	@Test
	void rowsStayUnpublishedWhileBrokerIsDownAndArePublishedWhenItIsBack(CapturedOutput output) {
		broker.stop();
		// Kopan bağlantının kendi logları (amqp-client) kesinti penceresine karışmasın.
		await().pollDelay(Duration.ofSeconds(1)).until(() -> true);
		int mark = output.getAll().length();

		List<UUID> ids = OutboxTestRows.newIds(2);
		OutboxTestRows.insert(jdbc, ids);
		String first = ids.get(0).toString();
		String second = ids.get(1).toString();

		await().atMost(TIMEOUT).until(() -> publisher.attemptsFor(first) >= 3);
		assertThat(publishedAt(first)).isNull();
		assertThat(publishedAt(second)).isNull();
		assertThat(publisher.attemptsFor(second)).isZero();

		String outage = output.getAll().substring(mark);
		List<String> relayLines = outage.lines().filter(line -> line.contains("OutboxRelay")).toList();
		assertThat(relayLines).hasSizeGreaterThanOrEqualTo(3)
			.allSatisfy(line -> assertThat(line).contains(" WARN ")
				.contains("Outbox publish failed, will retry on next poll: id=" + first + ", eventType=BookUpserted, "
						+ "error=OutboxPublishException: Send failed (AmqpConnectException)"));
		assertThat(outage).doesNotContain("\tat ").doesNotContain(" ERROR ").doesNotContain("Caused by");

		// Broker açılırken ve test kuyruğu bağlanırken worker bekletilir; yoksa mesaj kuyruksuz exchange'e düşebilir.
		publisher.failOn(id -> true);
		broker = startBroker();
		String queue = bindTemporaryQueue();
		publisher.failOn(id -> false);

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(publishedAt(first)).isNotNull();
			assertThat(publishedAt(second)).isNotNull();
		});
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(2));
		List<String> received = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			received.add(rabbitTemplate.receive(queue).getMessageProperties().getMessageId());
		}
		assertThat(received).containsExactly(first, second);
	}

	private static RabbitMQContainer startBroker() {
		RabbitMQContainer container = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));
		container.setPortBindings(List.of(AMQP_PORT + ":5672"));
		container.start();
		return container;
	}

	private String bindTemporaryQueue() {
		Queue temporary = new AnonymousQueue();
		String name = amqpAdmin.declareQueue(temporary);
		amqpAdmin.declareBinding(BindingBuilder.bind(temporary).to(eventsExchange).with("book.#"));
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
