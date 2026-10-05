package com.kitapsepeti.order.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.kitapsepeti.order.ApiTestSupport;
import com.kitapsepeti.order.client.Downstream;
import com.kitapsepeti.order.client.DownstreamCircuitBreakers;
import com.kitapsepeti.order.config.PaymentResultsConsumerConfig;
import com.kitapsepeti.order.entity.AddressSnapshot;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderLine;
import com.kitapsepeti.order.gateway.PaymentGateway;
import com.kitapsepeti.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Bekleyen sipariş uzlaştırma görevi: görev doğrudan tetiklenir, Payment ve Catalog WireMock. Özellikler
 * {@code PaymentResultListenerIT} ile birebir aynı: bağlam paylaşılır ve gerçek Payment sonucu tüketicisi açıktır
 * (yarış ve "expire sonrası geç ödeme" uçtan uca).
 */
@TestPropertySource(properties = {
		"app.payment-results.enabled=true",
		"app.outbox.enabled=true",
		"app.outbox.poll-interval=200ms"
})
@ExtendWith(OutputCaptureExtension.class)
class PendingReconciliationJobIT extends ApiTestSupport {

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private static final Duration MIN_AGE = Duration.ofSeconds(60);

	private static final Duration EXPIRE_AFTER = Duration.ofMinutes(10);

	private static final Duration YOUNG = Duration.ofMinutes(2);

	private static final Duration OLD = Duration.ofMinutes(11);

	private static final String PAYMENTS = "/internal/payments";

	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
		.withZone(ZoneOffset.UTC);

	@Autowired
	private OrderRepository orders;

	@Autowired
	private PaymentGateway paymentGateway;

	@Autowired
	private DownstreamCircuitBreakers breakers;

	@Autowired
	private Clock clock;

	@Autowired
	private RabbitTemplate rabbit;

	@Autowired
	private AmqpAdmin admin;

	@Autowired
	private TopicExchange eventsExchange;

	private PendingReconciliationJob job;

	@BeforeEach
	void setUp() {
		for (var stub : List.of(CATALOG, PAYMENT)) {
			stub.ensureRunning();
			stub.server().resetAll();
		}
		for (Downstream downstream : Downstream.values()) {
			this.breakers.get(downstream).reset();
		}
		this.admin.purgeQueue(PaymentResultsConsumerConfig.QUEUE, true);
		this.admin.purgeQueue(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE, true);
		this.jdbc.update("DELETE FROM outbox");
		this.jdbc.update("DELETE FROM order_status_history");
		this.jdbc.update("DELETE FROM order_items");
		this.jdbc.update("DELETE FROM orders");
		this.job = job(50);
	}

	@AfterEach
	void tearDown() {
		for (var stub : List.of(CATALOG, PAYMENT)) {
			stub.ensureRunning();
			stub.server().resetAll();
		}
		for (Downstream downstream : Downstream.values()) {
			this.breakers.get(downstream).reset();
		}
		Mockito.reset(this.transactions);
	}

	// --- requested ---

	@Test
	void oldRequestedOrderFailsAsCheckoutInterruptedAndReleasesStock() {
		Fixture order = requestedOrder(Duration.ofMinutes(5));
		catalogReleases(order);

		assertThat(this.job.executeRound()).isEqualTo(1);

		assertThat(status(order)).isEqualTo("failed");
		assertThat(failureCode(order)).isEqualTo("CHECKOUT_INTERRUPTED");
		assertThat(outboxTypes(order)).containsExactly("OrderFailed");
		awaitStock(order, "released");
		CATALOG.server().verify(1, postRequestedFor(urlEqualTo(release(order))));
		assertThat(paymentRequests()).isZero();
	}

	// --- held + Payment sonucu ---

	@Test
	void heldOrderWithSucceededPaymentIsPaidLikeTheConsumer() {
		Fixture order = heldOrder(YOUNG, false);
		UUID paymentId = UUID.randomUUID();
		paymentReturns(order, paymentId, "succeeded", null);
		catalogCommits(order);

		assertThat(this.job.executeRound()).isEqualTo(1);

		assertThat(status(order)).isEqualTo("paid");
		assertThat(paymentId(order)).isEqualTo(paymentId);
		assertThat(outboxTypes(order)).containsExactlyInAnyOrder("OrderPaid", "CartCheckedOut");
		assertThat(historyCount(order)).isEqualTo(2);
		awaitStock(order, "committed");
		PAYMENT.server()
			.verify(1, postRequestedFor(urlEqualTo(PAYMENTS)).withRequestBody(equalToJson("""
					{"orderId":"%s","userId":"%s","amount":10.00,"currency":"TRY"}"""
				.formatted(order.orderId(), order.userId()))));
	}

	@ParameterizedTest
	@CsvSource(value = { "CARD_DECLINED, CARD_DECLINED", "NULL, PAYMENT_FAILED", "free text, PAYMENT_FAILED" },
			nullValues = "NULL")
	void heldOrderWithFailedPaymentFailsWithPaymentCodeAndReleasesStock(String paymentCode, String expectedCode) {
		Fixture order = heldOrder(YOUNG, true);
		paymentReturns(order, order.paymentId(), "failed", paymentCode);
		catalogReleases(order);

		this.job.executeRound();

		assertThat(status(order)).isEqualTo("failed");
		assertThat(failureCode(order)).isEqualTo(expectedCode);
		assertThat(outboxTypes(order)).containsExactly("OrderFailed");
		awaitStock(order, "released");
	}

	@Test
	void youngHeldOrderWithInitiatedPaymentStaysPendingAndGetsPaymentAttached() {
		Fixture order = heldOrder(YOUNG, false);
		UUID paymentId = UUID.randomUUID();
		paymentReturns(order, paymentId, "initiated", null);

		this.job.executeRound();

		assertThat(status(order)).isEqualTo("pending");
		assertThat(paymentId(order)).isEqualTo(paymentId);
		assertThat(outboxCount(order)).isZero();
		assertThat(catalogRequests()).isZero();
	}

	@Test
	void oldHeldOrderWithInitiatedPaymentExpiresAndReleasesStock() {
		Fixture order = heldOrder(OLD, false);
		UUID paymentId = UUID.randomUUID();
		paymentReturns(order, paymentId, "initiated", null);
		catalogReleases(order);

		this.job.executeRound();

		assertThat(status(order)).isEqualTo("failed");
		assertThat(failureCode(order)).isEqualTo("ORDER_EXPIRED");
		assertThat(paymentId(order)).as("geç başarı aynı ödemeyle eşleşsin").isEqualTo(paymentId);
		assertThat(outboxTypes(order)).containsExactly("OrderFailed");
		awaitStock(order, "released");
	}

	enum NoResult {
		UNKNOWN,
		NOT_PERFORMED,
		REJECTED
	}

	@ParameterizedTest
	@EnumSource(NoResult.class)
	void youngHeldOrderWithoutPaymentResultIsUnchanged(NoResult mode, CapturedOutput output) {
		Fixture order = heldOrder(YOUNG, true);
		noPaymentResult(mode);

		this.job.executeRound();

		assertThat(status(order)).isEqualTo("pending");
		assertThat(stockState(order)).isEqualTo("held");
		assertThat(historyCount(order)).isEqualTo(1);
		assertThat(outboxCount(order)).isZero();
		assertThat(catalogRequests()).isZero();
		if (mode == NoResult.REJECTED) {
			assertThat(output.getOut().lines()).anySatisfy(line -> assertThat(line).contains("ERROR")
				.contains("Pending reconcile payment rejected by payment service (status=409, code=PAYMENT_ORDER_MISMATCH)"));
		}
	}

	@ParameterizedTest
	@EnumSource(NoResult.class)
	void oldHeldOrderWithoutPaymentResultExpires(NoResult mode) {
		Fixture order = heldOrder(OLD, true);
		noPaymentResult(mode);
		catalogReleases(order);

		this.job.executeRound();

		assertThat(status(order)).isEqualTo("failed");
		assertThat(failureCode(order)).isEqualTo("ORDER_EXPIRED");
		assertThat(outboxTypes(order)).containsExactly("OrderFailed");
		awaitStock(order, "released");
	}

	// --- seçim ---

	@Test
	void ordersYoungerThanMinAgeAndSettledOrdersAreNotSelected() {
		Fixture young = requestedOrder(Duration.ofSeconds(30));
		Fixture failed = requestedOrder(OLD);
		this.transactions.markFailed(failed.orderId(), "OUT_OF_STOCK");
		Fixture paid = heldOrder(OLD, true);
		catalogCommits(paid);
		this.transactions.reconcilePaymentSucceeded(paid.orderId(), paid.paymentId());
		awaitStock(paid, "committed");
		CATALOG.server().resetRequests();

		assertThat(this.job.executeRound()).isZero();

		assertThat(status(young)).isEqualTo("pending");
		assertThat(paymentRequests()).isZero();
		assertThat(catalogRequests()).isZero();
	}

	@Test
	void batchLimitIsRespectedOldestFirst() {
		Fixture oldest = requestedOrder(Duration.ofMinutes(9));
		Fixture middle = requestedOrder(Duration.ofMinutes(8));
		Fixture newest = requestedOrder(Duration.ofMinutes(7));
		catalogReleases(oldest);
		catalogReleases(middle);

		assertThat(job(2).executeRound()).isEqualTo(2);

		assertThat(status(oldest)).isEqualTo("failed");
		assertThat(status(middle)).isEqualTo("failed");
		assertThat(status(newest)).isEqualTo("pending");
	}

	@Test
	void openPaymentCircuitStopsPaymentCallsButStillExpiresAndInterrupts() {
		Fixture expired = heldOrder(OLD, true);
		Fixture interrupted = requestedOrder(Duration.ofMinutes(5));
		Fixture waiting = heldOrder(YOUNG, true);
		catalogReleases(expired);
		catalogReleases(interrupted);
		this.breakers.get(Downstream.PAYMENT).transitionToForcedOpenState();

		assertThat(this.job.executeRound()).isEqualTo(3);

		assertThat(failureCode(expired)).isEqualTo("ORDER_EXPIRED");
		assertThat(failureCode(interrupted)).isEqualTo("CHECKOUT_INTERRUPTED");
		assertThat(status(waiting)).isEqualTo("pending");
		assertThat(paymentRequests()).isZero();
		assertThat(this.breakers.get(Downstream.PAYMENT).getMetrics().getNumberOfNotPermittedCalls())
			.as("açık devreyi gören ilk çağrıdan sonra o tur Payment denenmez")
			.isEqualTo(1);
		awaitStock(expired, "released");
		awaitStock(interrupted, "released");
	}

	// --- tüketiciyle eşzamanlılık ---

	/** Görev ve PaymentSucceeded tüketicisi aynı siparişe aynı anda: tek paid, tek OrderPaid, tek stok commit. */
	@ParameterizedTest
	@ValueSource(ints = { 0, 300 })
	void jobAndConsumerRacingOnTheSameOrderPayItOnce(int paymentDelayMillis) throws Exception {
		Fixture order = heldOrder(YOUNG, true);
		PAYMENT.server()
			.stubFor(post(urlEqualTo(PAYMENTS)).willReturn(paymentResponse(order, order.paymentId(), "succeeded", null)
				.withFixedDelay(paymentDelayMillis)));
		catalogCommits(order);
		CyclicBarrier start = new CyclicBarrier(2);

		CompletableFuture<Integer> round = CompletableFuture.supplyAsync(() -> {
			arrive(start);
			return this.job.executeRound();
		});
		arrive(start);
		publish(succeededEvent(order, order.paymentId()));

		// Tüketici seçimden önce kazanırsa sipariş aday bile olmaz (0); sonra kazanırsa görevin geçişi no-op olur (1).
		assertThat(round.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isBetween(0, 1);
		awaitStock(order, "committed");
		await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(status(order)).isEqualTo("paid");
			assertThat(historyCount(order)).isEqualTo(2);
			assertThat(outboxTypes(order)).containsExactlyInAnyOrder("OrderPaid", "CartCheckedOut");
			assertThat(messageCount(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE)).isZero();
		});
		CATALOG.server().verify(1, postRequestedFor(urlEqualTo(commit(order))));
	}

	/** Uçtan uca: görev siparişi süresi dolduğu için kapatır, ardından Payment'ın başarı olayı gelir (iade listesi). */
	@Test
	void paymentSucceededAfterExpiryIsRecordedAsLatePayment(CapturedOutput output) {
		Fixture order = heldOrder(OLD, true);
		paymentReturns(order, order.paymentId(), "initiated", null);
		catalogReleases(order);
		this.job.executeRound();
		assertThat(failureCode(order)).isEqualTo("ORDER_EXPIRED");
		awaitStock(order, "released");

		publish(succeededEvent(order, order.paymentId()));

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(latePaymentRecorded(order)).isTrue());
		assertThat(status(order)).isEqualTo("failed");
		assertThat(failureCode(order)).isEqualTo("ORDER_EXPIRED");
		assertThat(stockState(order)).isEqualTo("released");
		assertThat(outboxTypes(order)).containsExactly("OrderFailed");
		assertThat(messageCount(PaymentResultsConsumerConfig.DEAD_LETTER_QUEUE)).isZero();
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(output.getOut().lines())
			.anySatisfy(line -> assertThat(line).contains("ERROR").contains("Payment result conflict -> LATE_PAYMENT_SUCCESS")));
	}

	// --- log ve plan ---

	@Test
	void roundSummaryIsOneInfoLineWithoutIdsOrAmounts(CapturedOutput output) {
		Fixture interrupted = requestedOrder(Duration.ofMinutes(5));
		Fixture expired = heldOrder(OLD, true);
		catalogReleases(interrupted);
		catalogReleases(expired);
		noPaymentResult(NoResult.UNKNOWN);
		int from = output.getAll().length();

		this.job.executeRound();

		String round = output.getAll().substring(from);
		assertThat(round.lines().filter(line -> line.contains("Pending reconcile round completed")))
			.singleElement()
			.satisfies(line -> assertThat(line).contains("INFO")
				.contains("Pending reconcile round completed: processed=2, counts=[interrupted=1, expired=1]"));
		awaitStock(interrupted, "released");
		awaitStock(expired, "released");
		assertThat(output.getAll().substring(from)).doesNotContain(interrupted.orderId().toString())
			.doesNotContain(interrupted.userId().toString())
			.doesNotContain(expired.orderId().toString())
			.doesNotContain(expired.paymentId().toString())
			.doesNotContain("10.00");
	}

	@Test
	void roundWithOnlyWaitingOrdersLogsNoSummary(CapturedOutput output) {
		heldOrder(YOUNG, true);
		noPaymentResult(NoResult.UNKNOWN);
		int from = output.getAll().length();

		assertThat(this.job.executeRound()).isEqualTo(1);

		assertThat(output.getAll().substring(from)).doesNotContain("Pending reconcile round completed");
	}

	/** Gerçekçi dağılım: siparişlerin çoğu kapanmış, azı bekliyor; istatistikler tazelenir (önceki testlerin silinen satırları). */
	@Test
	void candidateQueryUsesStatusCreatedIndexWithoutFilesort() {
		for (int i = 0; i < 40; i++) {
			Fixture failed = requestedOrder(OLD.plusMinutes(i));
			this.transactions.markFailed(failed.orderId(), "OUT_OF_STOCK");
		}
		for (int i = 0; i < 3; i++) {
			requestedOrder(Duration.ofMinutes(5 + i));
		}
		this.jdbc.queryForList("ANALYZE TABLE orders");

		List<Map<String, Object>> plan = this.jdbc.queryForList("""
				EXPLAIN SELECT o.id, o.user_id, o.stock_state, o.total_amount, o.currency, o.payment_id, o.created_at
				FROM orders o
				WHERE o.status = 'pending' AND o.created_at < NOW(6)
				ORDER BY o.created_at ASC
				LIMIT 50
				""");

		assertThat(plan).hasSize(1);
		assertThat(plan.get(0).get("key")).isEqualTo("ix_orders_status_created");
		assertThat(String.valueOf(plan.get(0).get("Extra"))).doesNotContain("filesort");
	}

	// --- yardımcılar ---

	private PendingReconciliationJob job(int batch) {
		return new PendingReconciliationJob(this.orders, this.transactions, this.paymentGateway, this.clock, MIN_AGE,
				EXPIRE_AFTER, batch);
	}

	private Fixture requestedOrder(Duration age) {
		UUID userId = UUID.randomUUID();
		Order order = Order.place(userId, UUID.randomUUID(), "TRY",
				List.of(new OrderLine(UUID.randomUUID(), "Book", 1, new BigDecimal("10.00"))),
				new AddressSnapshot("User", "5550000000", "Street", null, null, "Ankara", null, "TR"), this.clock);
		UUID orderId = this.transactions.insert(order).id();
		assertThat(this.jdbc.update("UPDATE orders SET created_at = ? WHERE id = UUID_TO_BIN(?)",
				UTC.format(this.clock.instant().minus(age)), orderId.toString()))
			.isEqualTo(1);
		return new Fixture(orderId, userId, null);
	}

	private Fixture heldOrder(Duration age, boolean withPayment) {
		Fixture requested = requestedOrder(age);
		this.transactions.markStockHeld(requested.orderId());
		if (!withPayment) {
			return requested;
		}
		UUID paymentId = UUID.randomUUID();
		this.transactions.attachPayment(requested.orderId(), paymentId);
		return new Fixture(requested.orderId(), requested.userId(), paymentId);
	}

	private void noPaymentResult(NoResult mode) {
		switch (mode) {
			case UNKNOWN -> PAYMENT.server().stubFor(post(urlEqualTo(PAYMENTS)).willReturn(problem(503,
					"PAYMENT_PROVIDER_UNAVAILABLE")));
			case NOT_PERFORMED -> PAYMENT.stop();
			case REJECTED -> PAYMENT.server().stubFor(post(urlEqualTo(PAYMENTS)).willReturn(problem(409,
					"PAYMENT_ORDER_MISMATCH")));
		}
	}

	private void paymentReturns(Fixture order, UUID paymentId, String status, String failureCode) {
		PAYMENT.server()
			.stubFor(post(urlEqualTo(PAYMENTS))
				.withRequestBody(matchingJsonPath("$.orderId", equalTo(order.orderId().toString())))
				.willReturn(paymentResponse(order, paymentId, status, failureCode)));
	}

	private static ResponseDefinitionBuilder paymentResponse(Fixture order, UUID paymentId, String status,
			String failureCode) {
		return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
				{"paymentId":"%s","orderId":"%s","amount":10.00,"currency":"TRY","status":"%s","failureCode":%s,
				 "redirectUrl":"https://provider.example/pay","createdAt":"2026-10-05T10:00:00Z",
				 "updatedAt":"2026-10-05T10:00:00Z"}"""
			.formatted(paymentId, order.orderId(), status, failureCode == null ? "null" : "\"" + failureCode + "\""));
	}

	private static ResponseDefinitionBuilder problem(int status, String code) {
		return aResponse().withStatus(status)
			.withHeader("Content-Type", "application/problem+json")
			.withBody("""
					{"type":"about:blank","title":"t","status":%d,"code":"%s","instance":"/x","traceId":"abc"}"""
				.formatted(status, code));
	}

	private void catalogReleases(Fixture order) {
		CATALOG.server().stubFor(post(urlEqualTo(release(order))).willReturn(okJson("""
				{"orderId":"%s","status":"released","expiresAt":"2026-10-05T12:00:00Z"}""".formatted(order.orderId()))));
	}

	private void catalogCommits(Fixture order) {
		CATALOG.server().stubFor(post(urlEqualTo(commit(order))).willReturn(okJson("""
				{"orderId":"%s","status":"committed","expiresAt":"2026-10-05T12:00:00Z"}""".formatted(order.orderId()))));
	}

	private static String release(Fixture order) {
		return "/internal/stock/reservations/" + order.orderId() + "/release";
	}

	private static String commit(Fixture order) {
		return "/internal/stock/reservations/" + order.orderId() + "/commit";
	}

	private int paymentRequests() {
		return PAYMENT.isRunning()
				? PAYMENT.server().countRequestsMatching(anyRequestedFor(anyUrl()).build()).getCount() : 0;
	}

	private int catalogRequests() {
		return CATALOG.server().countRequestsMatching(anyRequestedFor(anyUrl()).build()).getCount();
	}

	private Message succeededEvent(Fixture order, UUID paymentId) {
		String json = """
				{"eventVersion":1,"eventId":"%s","paymentId":"%s","orderId":"%s","amount":"10.00",\
				"currency":"TRY","occurredAt":"2026-10-05T10:00:00Z"}"""
			.formatted(UUID.randomUUID(), paymentId, order.orderId());
		return MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
			.setContentType(MessageProperties.CONTENT_TYPE_JSON)
			.setContentEncoding(StandardCharsets.UTF_8.name())
			.setType("PaymentSucceeded")
			.setMessageId(UUID.randomUUID().toString())
			.build();
	}

	private void publish(Message message) {
		this.rabbit.send(this.eventsExchange.getName(), "payment.succeeded", message);
	}

	private static void arrive(CyclicBarrier barrier) {
		try {
			barrier.await(5, TimeUnit.SECONDS);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private void awaitStock(Fixture order, String expected) {
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(stockState(order)).isEqualTo(expected));
	}

	private long messageCount(String queue) {
		QueueInformation info = this.admin.getQueueInfo(queue);
		return info == null ? -1 : info.getMessageCount();
	}

	private String status(Fixture order) {
		return column("status", order);
	}

	private String stockState(Fixture order) {
		return column("stock_state", order);
	}

	private String failureCode(Fixture order) {
		return column("failure_code", order);
	}

	private UUID paymentId(Fixture order) {
		String value = column("BIN_TO_UUID(payment_id)", order);
		return value == null ? null : UUID.fromString(value);
	}

	private boolean latePaymentRecorded(Fixture order) {
		return "1".equals(column("late_payment_at IS NOT NULL", order));
	}

	private String column(String expression, Fixture order) {
		return this.jdbc.queryForObject("SELECT " + expression + " FROM orders WHERE id = UUID_TO_BIN(?)",
				String.class, order.orderId().toString());
	}

	private int historyCount(Fixture order) {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = UUID_TO_BIN(?)",
				Integer.class, order.orderId().toString());
	}

	private int outboxCount(Fixture order) {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_id = UUID_TO_BIN(?)",
				Integer.class, order.orderId().toString());
	}

	private List<String> outboxTypes(Fixture order) {
		return this.jdbc.queryForList(
				"SELECT event_type FROM outbox WHERE aggregate_id = UUID_TO_BIN(?) ORDER BY created_at, id", String.class,
				order.orderId().toString());
	}

	private record Fixture(UUID orderId, UUID userId, UUID paymentId) {
	}

}
