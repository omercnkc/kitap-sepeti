package com.kitapsepeti.payment.provider.mock;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.payment.RabbitTestcontainersConfiguration;
import com.kitapsepeti.payment.TestcontainersConfiguration;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.repository.PaymentRepository;
import com.kitapsepeti.payment.service.PaymentResults;
import com.kitapsepeti.payment.service.WebhookService;
import com.kitapsepeti.payment.support.InternalTestKeys;
import com.kitapsepeti.payment.support.MutableClock;
import com.kitapsepeti.payment.support.WebhookTestSecrets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.web.client.RestClient;

/**
 * Mock webhook akış testlerinin tabanı: gerçek port, gerçek HTTP (uygulama kendi webhook ucuna gönderir),
 * Testcontainers MySQL ve RabbitMQ, outbox worker açık. Otomatik gönderim ve kurtarma görevi açık; görevin kendi
 * turu fiilen hiç gelmez (1 saat), testler {@link MockRecoveryJob#resendStale} ile elle tetikler. Uygulama saati
 * {@link MutableClock}: ödemenin {@code created_at}'i, imza zaman damgası ve doğrulama aynı saati kullanır.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, RabbitTestcontainersConfiguration.class,
		MockFlowTestSupport.ClockConfig.class })
@TestPropertySource(properties = { "app.outbox.enabled=true", "app.outbox.poll-interval=200ms",
		"app.payment.mock.dispatch.enabled=true", "app.payment.mock.delay=1s", "app.payment.mock.recovery.enabled=true",
		"app.payment.mock.recovery.interval=1h", "app.payment.mock.recovery.min-age=10s",
		"app.payment.mock.recovery.batch-size=3" })
abstract class MockFlowTestSupport {

	static final Duration TIMEOUT = Duration.ofSeconds(15);

	static final Duration DELAY = Duration.ofSeconds(1);

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock();
		}

	}

	@Value("${local.server.port}")
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	MockWebhookDispatcher dispatcher;

	@Autowired
	MockRecoveryJob recoveryJob;

	@Autowired
	PaymentResults results;

	@Autowired
	AmqpAdmin amqpAdmin;

	@Autowired
	TopicExchange eventsExchange;

	/** Gerçek repository; yarış testi eski bir anlık görüntü döndürür (her testten sonra sıfırlanır). */
	@MockitoSpyBean
	PaymentRepository payments;

	/** Webhook ucunun servisi; çağrılar sayılır, hata taklit edilir. Transaction proxy'sinin içindeki spy. */
	@MockitoSpyBean
	WebhookService webhookService;

	private final List<String> queues = new ArrayList<>();

	private RestClient api;

	@DynamicPropertySource
	static void secrets(DynamicPropertyRegistry registry) {
		InternalTestKeys.register(registry);
		WebhookTestSecrets.register(registry);
	}

	/** Önceki testin planlanmış gönderimleri bitmeden tablolar silinmez; sayılan çağrılara karışmazlar. */
	@BeforeEach
	void resetState() {
		clock.useSystemTime();
		await().atMost(TIMEOUT).until(() -> dispatcher.pendingCount() == 0);
		Mockito.clearInvocations(webhookSpy());
		jdbc.update("DELETE FROM outbox");
		jdbc.update("DELETE FROM provider_events");
		jdbc.update("DELETE FROM payments");
		api = RestClient.builder()
			.baseUrl("http://localhost:" + port)
			.defaultHeader(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY)
			.build();
	}

	@AfterEach
	void cleanUp() {
		clock.useSystemTime();
		queues.forEach(amqpAdmin::deleteQueue);
		queues.clear();
	}

	WebhookService webhookSpy() {
		return AopTestUtils.getUltimateTargetObject(webhookService);
	}

	record ApiResponse(int status, String body) {

		String field(String name) {
			return JsonPath.read(body, "$." + name);
		}

		UUID paymentId() {
			return UUID.fromString(field("paymentId"));
		}

	}

	ApiResponse create(UUID orderId, UUID userId, String amount) {
		String body = """
				{"orderId":"%s","userId":"%s","amount":%s,"currency":"TRY"}""".formatted(orderId, userId, amount);
		return api.post()
			.uri("/internal/payments")
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), read(response.getBody())));
	}

	ApiResponse create(String amount) {
		return create(UUID.randomUUID(), UUID.randomUUID(), amount);
	}

	ApiResponse get(UUID paymentId) {
		return api.get()
			.uri("/internal/payments/{id}", paymentId)
			.exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), read(response.getBody())));
	}

	private static String read(InputStream body) throws IOException {
		return new String(body.readAllBytes(), StandardCharsets.UTF_8);
	}

	/** Uygulama saatinin şu anki değeriyle oluşturulan ödeme; olay yayınlanmaz (otomatik gönderim yok). */
	Payment newPayment(String amount, boolean withReference, PaymentProviderType provider) {
		Payment payment = Payment.initiate(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(amount), "TRY",
				provider, clock);
		if (withReference) {
			payment.attachProviderReference(provider.dbValue() + "_" + UUID.randomUUID(), clock);
		}
		return payments.saveAndFlush(payment);
	}

	Payment newPayment(String amount) {
		return newPayment(amount, true, PaymentProviderType.MOCK);
	}

	/** Verilen anda oluşturulmuş referanslı mock ödeme. */
	Payment newPaymentAt(Instant createdAt, String amount) {
		clock.fixAt(createdAt);
		return newPayment(amount);
	}

	String status(UUID paymentId) {
		return jdbc.queryForObject("SELECT status FROM payments WHERE id = UUID_TO_BIN(?)", String.class,
				paymentId.toString());
	}

	Map<String, Object> paymentRow(UUID paymentId) {
		return jdbc.queryForMap("SELECT status, failure_code FROM payments WHERE id = UUID_TO_BIN(?)",
				paymentId.toString());
	}

	List<String> providerEventIds() {
		return jdbc.queryForList("SELECT provider_event_id FROM provider_events ORDER BY provider_event_id",
				String.class);
	}

	int outboxCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class);
	}

	String bindTemporaryQueue(String pattern) {
		Queue temporary = new AnonymousQueue();
		String name = amqpAdmin.declareQueue(temporary);
		queues.add(name);
		amqpAdmin.declareBinding(BindingBuilder.bind(temporary).to(eventsExchange).with(pattern));
		return name;
	}

	long messageCount(String queue) {
		QueueInformation info = amqpAdmin.getQueueInfo(queue);
		return (info == null) ? -1 : info.getMessageCount();
	}

}
