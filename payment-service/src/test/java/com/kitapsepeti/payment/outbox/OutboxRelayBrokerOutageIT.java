package com.kitapsepeti.payment.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.payment.TestcontainersConfiguration;
import com.kitapsepeti.payment.entity.TransitionResult;
import com.kitapsepeti.payment.service.PaymentResults;
import com.kitapsepeti.payment.support.InternalTestKeys;
import com.kitapsepeti.payment.support.WebhookTestSecrets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Gerçek broker kesintisi (catalog'daki test yöntemi). Broker sabit bir host portunda koşar ki durdurulup yeniden
 * açıldığında uygulamanın bağlantı ayarı geçerli kalsın; bu yüzden paylaşılan RabbitMQ konteyneri yerine kendi
 * konteynerini yönetir. Kesintide ödeme oluşturma ve readiness etkilenmez; olay outbox'ta bekler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, FaultInjectingPublisher.Config.class })
@TestPropertySource(properties = { "app.outbox.enabled=true", "app.outbox.poll-interval=200ms" })
@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayBrokerOutageIT {

	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private static final int AMQP_PORT = freePort();

	private static RabbitMQContainer broker = startBroker();

	@Autowired
	private MockMvc mockMvc;

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

	@Autowired
	private PaymentResults results;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.rabbitmq.host", broker::getHost);
		registry.add("spring.rabbitmq.port", () -> AMQP_PORT);
		registry.add("spring.rabbitmq.username", broker::getAdminUsername);
		registry.add("spring.rabbitmq.password", broker::getAdminPassword);
		InternalTestKeys.register(registry);
		WebhookTestSecrets.register(registry);
	}

	@AfterAll
	static void stopBroker() {
		broker.stop();
	}

	@Test
	void paymentsKeepWorkingWhileBrokerIsDownAndEventsArePublishedWhenItIsBack(CapturedOutput output)
			throws Exception {
		broker.stop();
		// Kopan bağlantının kendi logları (amqp-client) kesinti penceresine karışmasın.
		await().pollDelay(Duration.ofSeconds(1)).until(() -> true);
		int mark = output.getAll().length();

		UUID orderId = UUID.randomUUID();
		String created = mockMvc
			.perform(post("/internal/payments")
				.header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"orderId\":\"" + orderId + "\",\"userId\":\"" + UUID.randomUUID()
						+ "\",\"amount\":10.99,\"currency\":\"TRY\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID paymentId = UUID.fromString(JsonPath.read(created, "$.paymentId"));

		mockMvc.perform(get("/actuator/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
		mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());

		assertThat(results.recordSucceeded(paymentId)).isEqualTo(TransitionResult.APPLIED);
		String eventId = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM outbox WHERE aggregate_id = UUID_TO_BIN(?)",
				String.class, paymentId.toString());

		await().atMost(TIMEOUT).until(() -> publisher.attemptsFor(eventId) >= 3);
		assertThat(publishedAt(eventId)).isNull();

		String outage = output.getAll().substring(mark);
		List<String> relayLines = outage.lines().filter(line -> line.contains("OutboxRelay")).toList();
		assertThat(relayLines).hasSizeGreaterThanOrEqualTo(3)
			.allSatisfy(line -> assertThat(line).contains(" WARN ")
				.contains("Outbox publish failed, will retry on next poll: id=" + eventId
						+ ", eventType=PaymentSucceeded, error=OutboxPublishException: Send failed (AmqpConnectException)"));
		assertThat(outage).doesNotContain("\tat ")
			.doesNotContain(" ERROR ")
			.doesNotContain("Caused by")
			.doesNotContain(paymentId.toString())
			.doesNotContain(orderId.toString())
			.doesNotContain("10.99");

		// Kök health tüm katkıları (rabbit dahil) toplar; compose/orkestrasyon readiness'a bakar. Rabbit katkısının
		// hata logu (Spring Boot, stack trace'li WARN) kesinti penceresinin log kontrolünden sonra tetiklenir.
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().json("{\"status\":\"DOWN\"}", JsonCompareMode.LENIENT));

		// Broker açılırken ve test kuyruğu bağlanırken worker bekletilir; yoksa mesaj kuyruksuz exchange'e düşebilir.
		publisher.failOn(id -> true);
		broker = startBroker();
		String queue = bindTemporaryQueue();
		publisher.failOn(id -> false);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(publishedAt(eventId)).isNotNull());
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(1));
		Message message = rabbitTemplate.receive(queue);
		assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId);
		assertThat(message.getMessageProperties().getType()).isEqualTo("PaymentSucceeded");
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	private static int freePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
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
		amqpAdmin.declareBinding(BindingBuilder.bind(temporary).to(eventsExchange).with("payment.#"));
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
