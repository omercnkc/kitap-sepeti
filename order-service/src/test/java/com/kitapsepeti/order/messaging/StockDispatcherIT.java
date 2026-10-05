package com.kitapsepeti.order.messaging;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.kitapsepeti.order.ApiTestSupport;
import com.kitapsepeti.order.config.PaymentResultsConsumerConfig;
import com.kitapsepeti.order.entity.AddressSnapshot;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderLine;
import com.kitapsepeti.order.service.StockCoordinator;
import com.kitapsepeti.order.service.StockDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
		"app.payment-results.enabled=true"
})
@ExtendWith(OutputCaptureExtension.class)
class StockDispatcherIT extends ApiTestSupport {

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	@Autowired
	private RabbitTemplate rabbit;

	@Autowired
	private AmqpAdmin admin;

	@Autowired
	private TopicExchange eventsExchange;

	@Autowired
	private Clock clock;

	@Autowired
	private StockCoordinator coordinator;

	@BeforeEach
	void clean() {
		CATALOG.ensureRunning();
		CATALOG.server().resetAll();
		this.admin.purgeQueue(PaymentResultsConsumerConfig.QUEUE, true);
		this.admin.purgeQueue(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE, true);
		this.jdbc.update("DELETE FROM outbox");
		this.jdbc.update("DELETE FROM order_status_history");
		this.jdbc.update("DELETE FROM order_items");
		this.jdbc.update("DELETE FROM orders");
		clearInvocations(this.transactions);
	}

	@AfterEach
	void reset() {
		org.mockito.Mockito.reset(this.transactions);
		CATALOG.server().resetAll();
	}

	@Test
	void paymentSucceededDispatchesCommitAndMarksCommitted() {
		Fixture fixture = pendingHeldOrder();
		String commitPath = "/internal/stock/reservations/" + fixture.orderId() + "/commit";
		CATALOG.server().stubFor(post(urlEqualTo(commitPath))
			.willReturn(okJson("""
					{"orderId":"%s","status":"committed","expiresAt":"2026-10-05T12:00:00Z"}
					""".formatted(fixture.orderId()))));

		publish("payment.succeeded", succeeded(fixture));

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(status(fixture.orderId())).isEqualTo("paid");
			assertThat(stockState(fixture.orderId())).isEqualTo("committed");
		});
		CATALOG.server().verify(1, postRequestedFor(urlEqualTo(commitPath)));
	}

	@Test
	void paymentFailedDispatchesReleaseAndMarksReleased() {
		Fixture fixture = pendingHeldOrder();
		String releasePath = "/internal/stock/reservations/" + fixture.orderId() + "/release";
		CATALOG.server().stubFor(post(urlEqualTo(releasePath))
			.willReturn(okJson("""
					{"orderId":"%s","status":"released","expiresAt":"2026-10-05T12:00:00Z"}
					""".formatted(fixture.orderId()))));

		publish("payment.failed", failed(fixture, "CARD_DECLINED"));

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(status(fixture.orderId())).isEqualTo("failed");
			assertThat(stockState(fixture.orderId())).isEqualTo("released");
		});
		CATALOG.server().verify(1, postRequestedFor(urlEqualTo(releasePath)));
	}

	@Test
	void commitAlreadyReleasedMarksLostAndLogsError(CapturedOutput output) {
		Fixture fixture = pendingHeldOrder();
		String commitPath = "/internal/stock/reservations/" + fixture.orderId() + "/commit";
		CATALOG.server().stubFor(post(urlEqualTo(commitPath))
			.willReturn(aResponse()
				.withStatus(409)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("""
						{"type":"about:blank","title":"Reservation Released","status":409,"code":"RESERVATION_RELEASED"}
						""")));

		publish("payment.succeeded", succeeded(fixture));

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(status(fixture.orderId())).isEqualTo("paid");
			assertThat(stockState(fixture.orderId())).isEqualTo("lost");
		});
		assertThat(output.getAll()).contains("STOCK_COMMIT_LOST");
		assertThat(output.getAll()).doesNotContain(fixture.orderId().toString())
			.doesNotContain(fixture.paymentId().toString())
			.doesNotContain("10.00");
	}

	@Test
	void commit404ResourceNotFoundMarksLostAndLogsError(CapturedOutput output) {
		Fixture fixture = pendingHeldOrder();
		String commitPath = "/internal/stock/reservations/" + fixture.orderId() + "/commit";
		CATALOG.server().stubFor(post(urlEqualTo(commitPath))
			.willReturn(aResponse()
				.withStatus(404)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("""
						{"type":"about:blank","title":"Not Found","status":404,"code":"RESOURCE_NOT_FOUND"}
						""")));

		publish("payment.succeeded", succeeded(fixture));

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(status(fixture.orderId())).isEqualTo("paid");
			assertThat(stockState(fixture.orderId())).isEqualTo("lost");
		});
		assertThat(output.getAll()).contains("STOCK_COMMIT_LOST");
		assertThat(output.getAll()).doesNotContain(fixture.orderId().toString())
			.doesNotContain(fixture.paymentId().toString())
			.doesNotContain("10.00");
	}

	@Test
	void commit401KeepsHeldAndLogsError(CapturedOutput output) {
		Fixture fixture = pendingHeldOrder();
		String commitPath = "/internal/stock/reservations/" + fixture.orderId() + "/commit";
		CATALOG.server().stubFor(post(urlEqualTo(commitPath))
			.willReturn(aResponse()
				.withStatus(401)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("""
						{"type":"about:blank","title":"Unauthorized","status":401,"code":"UNAUTHORIZED"}
						""")));

		publish("payment.succeeded", succeeded(fixture));

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(status(fixture.orderId())).isEqualTo("paid");
			assertThat(stockState(fixture.orderId())).isEqualTo("held");
		});
		assertThat(output.getAll()).contains("Stock commit rejected by catalog");
		assertThat(output.getAll()).doesNotContain(fixture.orderId().toString())
			.doesNotContain(fixture.paymentId().toString())
			.doesNotContain("10.00");
	}

	@Test
	void commitUnknownServerErrorKeepsHeldAndLogsWarn(CapturedOutput output) {
		Fixture fixture = pendingHeldOrder();
		String commitPath = "/internal/stock/reservations/" + fixture.orderId() + "/commit";
		CATALOG.server().stubFor(post(urlEqualTo(commitPath))
			.willReturn(aResponse()
				.withStatus(500)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("""
						{"type":"about:blank","title":"Internal Error","status":500,"code":"INTERNAL_ERROR"}
						""")));

		publish("payment.succeeded", succeeded(fixture));

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(status(fixture.orderId())).isEqualTo("paid");
			assertThat(stockState(fixture.orderId())).isEqualTo("held");
		});
		assertThat(output.getAll()).contains("Stock commit unknown result");
		assertThat(output.getAll()).doesNotContain(fixture.orderId().toString());
	}

	@Test
	void transactionRollbackDoesNotDispatchCatalogCall() {
		Fixture fixture = pendingHeldOrder();
		String commitPath = "/internal/stock/reservations/" + fixture.orderId() + "/commit";
		CATALOG.server().stubFor(post(urlEqualTo(commitPath))
			.willReturn(okJson("""
					{"orderId":"%s","status":"committed","expiresAt":"2026-10-05T12:00:00Z"}
					""".formatted(fixture.orderId()))));

		doThrow(new CannotAcquireLockException("lock conflict"))
			.when(this.transactions)
			.applyPaymentSucceeded(any(), any(), any(), any());

		publish("payment.succeeded", succeeded(fixture));

		await().atMost(TIMEOUT).untilAsserted(() -> {
			QueueInformation dlq = this.admin.getQueueInfo(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE);
			assertThat(dlq != null && dlq.getMessageCount() == 1).isTrue();
		});
		CATALOG.server().verify(0, postRequestedFor(urlEqualTo(commitPath)));
	}

	@Test
	void duplicatePaymentMessageDoesNotCallCommitTwice() {
		Fixture fixture = pendingHeldOrder();
		String commitPath = "/internal/stock/reservations/" + fixture.orderId() + "/commit";
		CATALOG.server().stubFor(post(urlEqualTo(commitPath))
			.willReturn(okJson("""
					{"orderId":"%s","status":"committed","expiresAt":"2026-10-05T12:00:00Z"}
					""".formatted(fixture.orderId()))));

		publish("payment.succeeded", succeeded(fixture));
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(stockState(fixture.orderId())).isEqualTo("committed"));

		publish("payment.succeeded", succeeded(fixture));
		await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
			CATALOG.server().verify(1, postRequestedFor(urlEqualTo(commitPath)));
		});
	}

	@Test
	void executorQueueFullSkipsTaskLogsWarnWithoutException(CapturedOutput output) throws Exception {
		Fixture fixture = pendingHeldOrder();
		CountDownLatch blockLatch = new CountDownLatch(1);
		CountDownLatch taskStarted = new CountDownLatch(1);

		ThreadPoolExecutor customExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(1),
				(runnable, executorInstance) -> org.slf4j.LoggerFactory.getLogger(StockDispatcher.class)
					.warn("Stock dispatch executor queue is full; skipping task, StockSyncJob will recover"));

		StockDispatcher testDispatcher = new StockDispatcher(this.coordinator, customExecutor);
		try {
			// Görev 1: thread'i meşgul tut
			customExecutor.execute(() -> {
				taskStarted.countDown();
				try {
					blockLatch.await(5, TimeUnit.SECONDS);
				}
				catch (InterruptedException ignored) {
				}
			});
			assertThat(taskStarted.await(2, TimeUnit.SECONDS)).isTrue();

			// Görev 2: 1 kapasiteli kuyruğu doldur
			customExecutor.execute(() -> {});

			// Görev 3: kuyruk dolu -> rejected handler çağrılır, exception atılmaz
			assertThatCode(() -> testDispatcher.dispatchCommit(fixture.orderId())).doesNotThrowAnyException();

			assertThat(output.getAll()).contains("Stock dispatch executor queue is full; skipping task, StockSyncJob will recover");
			assertThat(stockState(fixture.orderId())).isEqualTo("held");
		}
		finally {
			blockLatch.countDown();
			testDispatcher.destroy();
		}
	}

	private Fixture pendingHeldOrder() {
		UUID paymentId = UUID.randomUUID();
		Order order = Order.place(UUID.randomUUID(), UUID.randomUUID(), "TRY",
				List.of(new OrderLine(UUID.randomUUID(), "Test Book", 1, new BigDecimal("10.00"))),
				new AddressSnapshot("Test User", "5550000000", "Test Street", null, null, "Ankara", null, "TR"),
				this.clock);
		UUID orderId = this.transactions.insert(order).id();
		this.transactions.markStockHeld(orderId);
		this.transactions.attachPayment(orderId, paymentId);
		clearInvocations(this.transactions);
		return new Fixture(orderId, paymentId);
	}

	private Message succeeded(Fixture fixture) {
		return message("PaymentSucceeded", """
				{"eventVersion":1,"eventId":"%s","paymentId":"%s","orderId":"%s","amount":"10.00",\
				"currency":"TRY","occurredAt":"2026-10-04T20:00:00Z"}"""
			.formatted(UUID.randomUUID(), fixture.paymentId(), fixture.orderId()));
	}

	private Message failed(Fixture fixture, String failureCode) {
		return message("PaymentFailed", """
				{"eventVersion":1,"eventId":"%s","paymentId":"%s","orderId":"%s","amount":"10.00",\
				"currency":"TRY","failureCode":"%s","occurredAt":"2026-10-04T20:00:00Z"}"""
			.formatted(UUID.randomUUID(), fixture.paymentId(), fixture.orderId(), failureCode));
	}

	private static Message message(String type, String json) {
		return MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
			.setContentType(MessageProperties.CONTENT_TYPE_JSON)
			.setContentEncoding(StandardCharsets.UTF_8.name())
			.setType(type)
			.setMessageId(UUID.randomUUID().toString())
			.build();
	}

	private void publish(String routingKey, Message message) {
		this.rabbit.send(this.eventsExchange.getName(), routingKey, message);
	}

	private String status(UUID orderId) {
		return this.jdbc.queryForObject("SELECT status FROM orders WHERE id = UUID_TO_BIN(?)", String.class,
				orderId.toString());
	}

	private String stockState(UUID orderId) {
		return this.jdbc.queryForObject("SELECT stock_state FROM orders WHERE id = UUID_TO_BIN(?)", String.class,
				orderId.toString());
	}

	private record Fixture(UUID orderId, UUID paymentId) {
	}

}
