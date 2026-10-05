package com.kitapsepeti.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.kitapsepeti.order.ApiTestSupport;
import com.kitapsepeti.order.config.PaymentResultsConsumerConfig;
import com.kitapsepeti.order.entity.AddressSnapshot;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderLine;
import com.kitapsepeti.order.service.OrderTransactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@TestPropertySource(properties = {
		"app.payment-results.enabled=true",
		"app.outbox.enabled=true",
		"app.outbox.poll-interval=200ms"
})
@ExtendWith(OutputCaptureExtension.class)
class PaymentResultListenerIT extends ApiTestSupport {

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
	private JsonMapper jsonMapper;

	@Autowired
	@Qualifier("paymentResultsTopology")
	private Declarables topology;

	@Autowired
	private RabbitListenerEndpointRegistry listenerRegistry;

	private final List<String> temporaryQueues = new ArrayList<>();

	@BeforeEach
	void clean() {
		this.admin.purgeQueue(PaymentResultsConsumerConfig.QUEUE, true);
		this.admin.purgeQueue(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE, true);
		this.jdbc.update("DELETE FROM outbox");
		this.jdbc.update("DELETE FROM order_status_history");
		this.jdbc.update("DELETE FROM order_items");
		this.jdbc.update("DELETE FROM orders");
		clearInvocations(this.transactions);
	}

	@AfterEach
	void cleanupQueues() {
		this.temporaryQueues.forEach(this.admin::deleteQueue);
		this.temporaryQueues.clear();
		org.mockito.Mockito.reset(this.transactions);
	}

	@Test
	void succeededMarksPaidWritesTwoEventsAndRelayPublishesBoth() {
		String captured = bindTemporary("order.#", "cart.#");
		Fixture fixture = pendingHeldOrder();

		publish("payment.succeeded", succeeded(fixture, fixture.paymentId(), "10.00", "TRY"));

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(status(fixture.orderId())).isEqualTo("paid");
			assertThat(historyCount(fixture.orderId())).isEqualTo(2);
			assertThat(outboxTypes(fixture.orderId())).containsExactlyInAnyOrder("OrderPaid", "CartCheckedOut");
			assertThat(unpublishedCount(fixture.orderId())).isZero();
			assertThat(messageCount(captured)).isEqualTo(2);
		});

		List<Message> published = List.of(this.rabbit.receive(captured), this.rabbit.receive(captured));
		assertThat(published).extracting(message -> message.getMessageProperties().getType())
			.containsExactlyInAnyOrder("OrderPaid", "CartCheckedOut");
		assertThat(published).extracting(message -> message.getMessageProperties().getReceivedRoutingKey())
			.containsExactlyInAnyOrder("order.paid", "cart.checked-out");

		Map<String, String> payloads = payloads(fixture.orderId());
		JsonNode paid = this.jsonMapper.readTree(payloads.get("OrderPaid"));
		assertThat(paid.propertyNames()).containsExactlyInAnyOrder("eventId", "eventVersion", "orderId", "userId",
				"paymentId", "totalAmount", "currency", "itemCount", "occurredAt");
		assertThat(paid.get("eventVersion").intValue()).isEqualTo(1);
		assertThat(paid.get("totalAmount").asText()).isEqualTo("10.00");
		JsonNode cart = this.jsonMapper.readTree(payloads.get("CartCheckedOut"));
		assertThat(cart.propertyNames())
			.containsExactlyInAnyOrder("eventId", "eventVersion", "cartId", "userId", "orderId", "occurredAt");
	}

	@Test
	void topologyAndListenerContainerMatchTheConsumerContract() {
		QueueInformation work = this.admin.getQueueInfo(PaymentResultsConsumerConfig.QUEUE);
		QueueInformation dead = this.admin.getQueueInfo(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE);
		assertThat(work).isNotNull();
		assertThat(dead).isNotNull();

		Queue declaredWork = this.topology.getDeclarablesByType(Queue.class)
			.stream()
			.filter(queue -> queue.getName().equals(PaymentResultsConsumerConfig.QUEUE))
			.findFirst()
			.orElseThrow();
		assertThat(declaredWork.isDurable()).isTrue();
		assertThat(declaredWork.getArguments())
			.containsEntry("x-dead-letter-exchange", PaymentResultsConsumerConfig.DEAD_LETTER_EXCHANGE)
			.containsEntry("x-dead-letter-routing-key", PaymentResultsConsumerConfig.DEAD_LETTER_ROUTING_KEY);
		assertThat(this.topology.getDeclarablesByType(DirectExchange.class))
			.extracting(DirectExchange::getName)
			.containsExactly(PaymentResultsConsumerConfig.DEAD_LETTER_EXCHANGE);
		assertThat(this.topology.getDeclarablesByType(Binding.class))
			.extracting(Binding::getRoutingKey)
			.containsExactly(PaymentResultsConsumerConfig.DEAD_LETTER_ROUTING_KEY, "payment.succeeded",
					"payment.failed");

		SimpleMessageListenerContainer container = (SimpleMessageListenerContainer) this.listenerRegistry
			.getListenerContainer(PaymentResultsConsumerConfig.LISTENER_ID);
		assertThat(container).isNotNull();
		assertThat(container.getActiveConsumerCount()).isEqualTo(1);
		assertThat(ReflectionTestUtils.getField(container, "prefetchCount")).isEqualTo(10);
	}

	@Test
	void duplicateDeliveryWritesNoSecondHistoryOrOutbox() {
		Fixture fixture = pendingHeldOrder();
		Message message = succeeded(fixture, fixture.paymentId(), "10.00", "TRY");

		publish("payment.succeeded", message);
		awaitPaid(fixture.orderId());
		publish("payment.succeeded", message);

		await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(historyCount(fixture.orderId())).isEqualTo(2);
			assertThat(outboxCount(fixture.orderId())).isEqualTo(2);
			assertThat(messageCount(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE)).isZero();
		});
	}

	@Test
	void failedUsesEventCodeWritesOnlyOrderFailedAndKeepsCartActive() {
		Fixture fixture = pendingHeldOrder();

		publish("payment.failed", failed(fixture, fixture.paymentId(), "CARD_DECLINED"));

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(status(fixture.orderId())).isEqualTo("failed");
			assertThat(failureCode(fixture.orderId())).isEqualTo("CARD_DECLINED");
			assertThat(outboxTypes(fixture.orderId())).containsExactly("OrderFailed");
		});
	}

	@Test
	void malformedFailureCodeFallsBackToPaymentFailed() {
		Fixture fixture = pendingHeldOrder();

		publish("payment.failed", failed(fixture, fixture.paymentId(), "provider free text"));

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(status(fixture.orderId())).isEqualTo("failed");
			assertThat(failureCode(fixture.orderId())).isEqualTo("PAYMENT_FAILED");
		});
	}

	enum PoisonCase {
		MALFORMED_JSON,
		UNKNOWN_TYPE,
		UNSUPPORTED_VERSION,
		MISSING_FIELD,
		UNKNOWN_ORDER,
		AMOUNT_MISMATCH,
		CURRENCY_MISMATCH
	}

	static Stream<PoisonCase> poisonCases() {
		return Stream.of(PoisonCase.values());
	}

	@ParameterizedTest
	@MethodSource("poisonCases")
	void poisonMessagesGoToDlqWithoutRetry(PoisonCase poisonCase) {
		Fixture fixture = pendingHeldOrder();
		Message message = switch (poisonCase) {
			case MALFORMED_JSON -> message("PaymentSucceeded", "{");
			case UNKNOWN_TYPE -> message("UnknownPayment", succeededBody(fixture, fixture.paymentId(), "10.00", "TRY", 1));
			case UNSUPPORTED_VERSION ->
				message("PaymentSucceeded", succeededBody(fixture, fixture.paymentId(), "10.00", "TRY", 2));
			case MISSING_FIELD -> message("PaymentSucceeded", """
					{"eventVersion":1,"eventId":"%s","paymentId":"%s","orderId":"%s","amount":"10.00","currency":"TRY"}"""
				.formatted(UUID.randomUUID(), fixture.paymentId(), fixture.orderId()));
			case UNKNOWN_ORDER -> message("PaymentSucceeded", succeededBody(
					new Fixture(UUID.randomUUID(), fixture.paymentId()), fixture.paymentId(), "10.00", "TRY", 1));
			case AMOUNT_MISMATCH -> succeeded(fixture, fixture.paymentId(), "10.01", "TRY");
			case CURRENCY_MISMATCH -> succeeded(fixture, fixture.paymentId(), "10.00", "USD");
		};
		clearInvocations(this.transactions);

		publish(poisonCase == PoisonCase.UNKNOWN_TYPE ? "payment.succeeded" : "payment.succeeded", message);

		await().atMost(TIMEOUT).untilAsserted(() ->
			assertThat(messageCount(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE)).isEqualTo(1));
		assertThat(status(fixture.orderId())).isEqualTo("pending");
		int transactionCalls = switch (poisonCase) {
			case UNKNOWN_ORDER, AMOUNT_MISMATCH, CURRENCY_MISMATCH -> 1;
			default -> 0;
		};
		verify(this.transactions, times(transactionCalls)).applyPaymentSucceeded(any(), any(), any(), any());
	}

	@Test
	void transientFailureIsRetriedAndThenSucceeds() {
		Fixture fixture = pendingHeldOrder();
		doThrow(new CannotAcquireLockException("temporary"))
			.doCallRealMethod()
			.when(this.transactions)
			.applyPaymentSucceeded(any(), any(), any(), any());

		publish("payment.succeeded", succeeded(fixture, fixture.paymentId(), "10.00", "TRY"));

		awaitPaid(fixture.orderId());
		verify(this.transactions, times(2)).applyPaymentSucceeded(any(), any(), any(), any());
		assertThat(messageCount(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE)).isZero();
	}

	@Test
	void persistentTransientFailureIsTriedThreeTimesThenDeadLettered() {
		Fixture fixture = pendingHeldOrder();
		doThrow(new CannotAcquireLockException("temporary"))
			.when(this.transactions)
			.applyPaymentSucceeded(any(), any(), any(), any());

		publish("payment.succeeded", succeeded(fixture, fixture.paymentId(), "10.00", "TRY"));

		await().atMost(TIMEOUT).untilAsserted(() ->
			assertThat(messageCount(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE)).isEqualTo(1));
		verify(this.transactions, times(3)).applyPaymentSucceeded(any(), any(), any(), any());
		assertThat(status(fixture.orderId())).isEqualTo("pending");
	}

	@Test
	void succeededForFailedOrderRecordsLatePaymentOnceWithoutDlqOrNewOutbox(CapturedOutput output) {
		Fixture fixture = pendingHeldOrder();
		this.transactions.markFailed(fixture.orderId(), "CARD_DECLINED");
		this.jdbc.update("DELETE FROM outbox WHERE aggregate_id = UUID_TO_BIN(?)", fixture.orderId().toString());
		clearInvocations(this.transactions);
		Message message = succeeded(fixture, fixture.paymentId(), "10.00", "TRY");

		publish("payment.succeeded", message);

		await().atMost(TIMEOUT).untilAsserted(() ->
			verify(this.transactions).applyPaymentSucceeded(any(), any(), any(), any()));
		assertThat(status(fixture.orderId())).isEqualTo("failed");
		assertThat(failureCode(fixture.orderId())).isEqualTo("CARD_DECLINED");
		Object firstRecord = latePaymentAt(fixture.orderId());
		assertThat(firstRecord).isNotNull();
		assertThat(outboxCount(fixture.orderId())).isZero();
		assertThat(historyCount(fixture.orderId())).isEqualTo(2);
		assertThat(messageCount(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE)).isZero();
		assertThat(output.getOut().lines())
			.anySatisfy(line -> assertThat(line).contains("ERROR").contains("Payment result conflict -> LATE_PAYMENT_SUCCESS"))
			.anySatisfy(line -> assertThat(line).contains("Payment result -> LATE_PAYMENT_SUCCESS"));

		publish("payment.succeeded", message);

		await().atMost(TIMEOUT).untilAsserted(() ->
			verify(this.transactions, times(2)).applyPaymentSucceeded(any(), any(), any(), any()));
		await().atMost(TIMEOUT).untilAsserted(() ->
			assertThat(output.getOut()).contains("Payment result -> ALREADY_IN_STATE"));
		assertThat(latePaymentAt(fixture.orderId())).isEqualTo(firstRecord);
		assertThat(status(fixture.orderId())).isEqualTo("failed");
		assertThat(outboxCount(fixture.orderId())).isZero();
		assertThat(messageCount(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE)).isZero();
	}

	/** Failed siparişe başka bir ödemenin başarısı: geç ödeme yine kaydedilir, bağlı ödeme değişmez. */
	@Test
	void succeededWithDifferentPaymentForFailedOrderRecordsLatePaymentWithoutReplacingPayment(CapturedOutput output) {
		Fixture fixture = pendingHeldOrder();
		this.transactions.markFailed(fixture.orderId(), "CARD_DECLINED");
		this.jdbc.update("DELETE FROM outbox WHERE aggregate_id = UUID_TO_BIN(?)", fixture.orderId().toString());
		clearInvocations(this.transactions);

		publish("payment.succeeded", succeeded(fixture, UUID.randomUUID(), "10.00", "TRY"));

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(latePaymentAt(fixture.orderId())).isNotNull());
		assertThat(paymentId(fixture.orderId())).isEqualTo(fixture.paymentId());
		assertThat(status(fixture.orderId())).isEqualTo("failed");
		assertThat(outboxCount(fixture.orderId())).isZero();
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(output.getOut().lines())
			.anySatisfy(line -> assertThat(line).contains("ERROR").contains("Payment result conflict -> LATE_PAYMENT_SUCCESS"))
			.anySatisfy(line -> assertThat(line).contains("ERROR").contains("Payment result conflict -> PAYMENT_ID_CONFLICT")));
	}

	/** Zehirli mesaj DLQ'ya gider; container'ın hata işleyicisi ERROR + stack trace yazmaz (tek satırlık özet yeter). */
	@Test
	void poisonMessageIsDeadLetteredWithoutStackTrace(CapturedOutput output) {
		int from = output.getAll().length();

		publish("payment.succeeded", message("PaymentSucceeded", "{"));

		await().atMost(TIMEOUT).untilAsserted(() ->
			assertThat(messageCount(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE)).isEqualTo(1));
		await().atMost(TIMEOUT).untilAsserted(() ->
			assertThat(output.getAll().substring(from)).contains("Payment result -> DLQ_MALFORMED"));
		assertThat(output.getAll().substring(from)).doesNotContain("\tat ")
			.doesNotContain("Caused by")
			.doesNotContain("Execution of Rabbit message listener failed");
	}

	@Test
	void differentPaymentIdIsAckedAsConflictWithoutChangingOrderOrWritingOutbox() {
		Fixture fixture = pendingHeldOrder();
		clearInvocations(this.transactions);

		publish("payment.succeeded", succeeded(fixture, UUID.randomUUID(), "10.00", "TRY"));

		await().atMost(TIMEOUT).untilAsserted(() ->
			verify(this.transactions).applyPaymentSucceeded(any(), any(), any(), any()));
		assertThat(status(fixture.orderId())).isEqualTo("pending");
		assertThat(outboxCount(fixture.orderId())).isZero();
		assertThat(messageCount(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE)).isZero();
	}

	@Test
	void consumerAndDlqLogsContainNoIdsOrAmounts(CapturedOutput output) {
		Fixture fixture = pendingHeldOrder();

		publish("payment.succeeded", succeeded(fixture, fixture.paymentId(), "10.01", "TRY"));

		await().atMost(TIMEOUT).untilAsserted(() ->
			assertThat(messageCount(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE)).isEqualTo(1));
		assertThat(output.getAll()).doesNotContain(fixture.orderId().toString())
			.doesNotContain(fixture.paymentId().toString())
			.doesNotContain("10.01")
			.doesNotContain("\tat ")
			.contains("AMOUNT_MISMATCH");
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

	private Message succeeded(Fixture fixture, UUID paymentId, String amount, String currency) {
		return message("PaymentSucceeded", succeededBody(fixture, paymentId, amount, currency, 1));
	}

	private Message failed(Fixture fixture, UUID paymentId, String failureCode) {
		return message("PaymentFailed", """
				{"eventVersion":1,"eventId":"%s","paymentId":"%s","orderId":"%s","amount":"10.00",\
				"currency":"TRY","failureCode":"%s","occurredAt":"2026-10-04T20:00:00Z"}"""
			.formatted(UUID.randomUUID(), paymentId, fixture.orderId(), failureCode));
	}

	private String succeededBody(Fixture fixture, UUID paymentId, String amount, String currency, int version) {
		return """
				{"eventVersion":%d,"eventId":"%s","paymentId":"%s","orderId":"%s","amount":"%s",\
				"currency":"%s","occurredAt":"2026-10-04T20:00:00Z"}"""
			.formatted(version, UUID.randomUUID(), paymentId, fixture.orderId(), amount, currency);
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

	private void awaitPaid(UUID orderId) {
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(status(orderId)).isEqualTo("paid"));
	}

	private String bindTemporary(String... patterns) {
		Queue queue = new AnonymousQueue();
		String name = this.admin.declareQueue(queue);
		this.temporaryQueues.add(name);
		for (String pattern : patterns) {
			this.admin.declareBinding(BindingBuilder.bind(queue).to(this.eventsExchange).with(pattern));
		}
		return name;
	}

	private long messageCount(String queue) {
		QueueInformation info = this.admin.getQueueInfo(queue);
		return info == null ? -1 : info.getMessageCount();
	}

	private String status(UUID orderId) {
		return this.jdbc.queryForObject("SELECT status FROM orders WHERE id = UUID_TO_BIN(?)", String.class,
				orderId.toString());
	}

	private String failureCode(UUID orderId) {
		return this.jdbc.queryForObject("SELECT failure_code FROM orders WHERE id = UUID_TO_BIN(?)", String.class,
				orderId.toString());
	}

	private Object latePaymentAt(UUID orderId) {
		return this.jdbc.queryForObject("SELECT late_payment_at FROM orders WHERE id = UUID_TO_BIN(?)", Object.class,
				orderId.toString());
	}

	private UUID paymentId(UUID orderId) {
		return UUID.fromString(this.jdbc.queryForObject(
				"SELECT BIN_TO_UUID(payment_id) FROM orders WHERE id = UUID_TO_BIN(?)", String.class, orderId.toString()));
	}

	private int historyCount(UUID orderId) {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = UUID_TO_BIN(?)",
				Integer.class, orderId.toString());
	}

	private int outboxCount(UUID orderId) {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_id = UUID_TO_BIN(?)", Integer.class,
				orderId.toString());
	}

	private int unpublishedCount(UUID orderId) {
		return this.jdbc.queryForObject("""
				SELECT COUNT(*) FROM outbox WHERE aggregate_id = UUID_TO_BIN(?) AND published_at IS NULL""",
				Integer.class, orderId.toString());
	}

	private List<String> outboxTypes(UUID orderId) {
		return this.jdbc.queryForList("""
				SELECT event_type FROM outbox WHERE aggregate_id = UUID_TO_BIN(?) ORDER BY created_at, id""",
				String.class, orderId.toString());
	}

	private Map<String, String> payloads(UUID orderId) {
		return this.jdbc.query("""
				SELECT event_type, payload FROM outbox WHERE aggregate_id = UUID_TO_BIN(?)""",
				rs -> {
					java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
					while (rs.next()) {
						result.put(rs.getString(1), rs.getString(2));
					}
					return result;
				}, orderId.toString());
	}

	private record Fixture(UUID orderId, UUID paymentId) {
	}

}
