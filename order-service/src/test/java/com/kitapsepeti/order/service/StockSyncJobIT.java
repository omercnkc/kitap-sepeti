package com.kitapsepeti.order.service;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kitapsepeti.order.ApiTestSupport;
import com.kitapsepeti.order.entity.AddressSnapshot;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderLine;
import com.kitapsepeti.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class StockSyncJobIT extends ApiTestSupport {

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private StockCoordinator coordinator;

	@Autowired
	private Clock clock;

	private StockSyncJob job;

	@BeforeEach
	void setUp() {
		CATALOG.ensureRunning();
		CATALOG.server().resetAll();
		this.jdbc.update("DELETE FROM outbox");
		this.jdbc.update("DELETE FROM order_status_history");
		this.jdbc.update("DELETE FROM order_items");
		this.jdbc.update("DELETE FROM orders");
		this.job = new StockSyncJob(this.orderRepository, this.coordinator, this.clock, Duration.ofSeconds(10), 50);
	}

	@AfterEach
	void tearDown() {
		CATALOG.server().resetAll();
	}

	@Test
	void paidHeldOlderThanMinAgeIsCommitted() {
		UUID orderId = insertOrder("paid", "held", Duration.ofSeconds(20));
		String commitPath = "/internal/stock/reservations/" + orderId + "/commit";
		CATALOG.server().stubFor(post(urlEqualTo(commitPath))
			.willReturn(okJson("""
					{"orderId":"%s","status":"committed","expiresAt":"2026-10-05T12:00:00Z"}
					""".formatted(orderId))));

		int processed = this.job.executeRound();

		assertThat(processed).isEqualTo(1);
		assertThat(stockState(orderId)).isEqualTo("committed");
		CATALOG.server().verify(1, postRequestedFor(urlEqualTo(commitPath)));
	}

	@Test
	void failedHeldAndFailedRequestedOlderThanMinAgeAreReleased() {
		UUID failedHeldId = insertOrder("failed", "held", Duration.ofSeconds(15));
		UUID failedRequestedId = insertOrder("failed", "requested", Duration.ofSeconds(25));

		CATALOG.server().stubFor(post(urlEqualTo("/internal/stock/reservations/" + failedHeldId + "/release"))
			.willReturn(okJson("""
					{"orderId":"%s","status":"released","expiresAt":"2026-10-05T12:00:00Z"}
					""".formatted(failedHeldId))));
		CATALOG.server().stubFor(post(urlEqualTo("/internal/stock/reservations/" + failedRequestedId + "/release"))
			.willReturn(okJson("""
					{"orderId":"%s","status":"released","expiresAt":"2026-10-05T12:00:00Z"}
					""".formatted(failedRequestedId))));

		int processed = this.job.executeRound();

		assertThat(processed).isEqualTo(2);
		assertThat(stockState(failedHeldId)).isEqualTo("released");
		assertThat(stockState(failedRequestedId)).isEqualTo("released");
	}

	@Test
	void ordersYoungerThanMinAgeAndPendingOrdersAreNotSelected() {
		UUID freshPaidHeld = insertOrder("paid", "held", Duration.ofSeconds(3));
		UUID pendingHeldOld = insertOrder("pending", "held", Duration.ofSeconds(60));
		UUID pendingRequestedOld = insertOrder("pending", "requested", Duration.ofSeconds(60));

		int processed = this.job.executeRound();

		assertThat(processed).isZero();
		assertThat(stockState(freshPaidHeld)).isEqualTo("held");
		assertThat(stockState(pendingHeldOld)).isEqualTo("held");
		assertThat(stockState(pendingRequestedOld)).isEqualTo("requested");
		CATALOG.server().verify(0, postRequestedFor(urlEqualTo("/internal/stock/reservations/" + freshPaidHeld + "/commit")));
	}

	@Test
	void batchLimitIsRespected() {
		StockSyncJob limitedJob = new StockSyncJob(this.orderRepository, this.coordinator, this.clock, Duration.ofSeconds(10), 2);

		UUID id1 = insertOrder("paid", "held", Duration.ofSeconds(50));
		UUID id2 = insertOrder("paid", "held", Duration.ofSeconds(40));
		UUID id3 = insertOrder("paid", "held", Duration.ofSeconds(30));

		CATALOG.server().stubFor(post(urlEqualTo("/internal/stock/reservations/" + id1 + "/commit"))
			.willReturn(okJson("""
					{"orderId":"%s","status":"committed","expiresAt":"2026-10-05T12:00:00Z"}
					""".formatted(id1))));
		CATALOG.server().stubFor(post(urlEqualTo("/internal/stock/reservations/" + id2 + "/commit"))
			.willReturn(okJson("""
					{"orderId":"%s","status":"committed","expiresAt":"2026-10-05T12:00:00Z"}
					""".formatted(id2))));

		int processed = limitedJob.executeRound();

		assertThat(processed).isEqualTo(2);
		assertThat(stockState(id1)).isEqualTo("committed");
		assertThat(stockState(id2)).isEqualTo("committed");
		assertThat(stockState(id3)).isEqualTo("held"); // 3. sipariş bu turda işlenmedi
	}

	@Test
	void circuitBreakerOpenTerminatesRoundEarly() {
		UUID id1 = insertOrder("paid", "held", Duration.ofSeconds(50));
		UUID id2 = insertOrder("paid", "held", Duration.ofSeconds(40));

		// Catalog sunucusunu durdur -> NotSent / NotPerformed (circuit breaker veya bağlantı reddi)
		CATALOG.stop();

		int processed = this.job.executeRound();

		assertThat(processed).isEqualTo(1); // 1. çağrıda NotPerformed aldığından turu erken kesti
		assertThat(stockState(id1)).isEqualTo("held");
		assertThat(stockState(id2)).isEqualTo("held");

		CATALOG.ensureRunning();
	}

	@Test
	void concurrentStateChangeMakesLockedReReadNoOp() {
		UUID orderId = insertOrder("paid", "held", Duration.ofSeconds(20));
		String commitPath = "/internal/stock/reservations/" + orderId + "/commit";
		CATALOG.server().stubFor(post(urlEqualTo(commitPath))
			.willReturn(okJson("""
					{"orderId":"%s","status":"committed","expiresAt":"2026-10-05T12:00:00Z"}
					""".formatted(orderId))));

		// Simüle et: Seçimden sonra başka bir thread siparişi committed yaptı
		this.transactions.markStockCommitted(orderId);

		// Catalog commit yanıtı sonrası kilitli okuma no-op olur
		StockOutcome outcome = this.coordinator.processCommit(orderId);

		assertThat(outcome).isEqualTo(StockOutcome.NO_OP);
		assertThat(stockState(orderId)).isEqualTo("committed");
	}

	@Test
	void infoSummaryLineIsLoggedWithoutIdsOrAmounts(CapturedOutput output) {
		UUID orderId = insertOrder("paid", "held", Duration.ofSeconds(20));
		String commitPath = "/internal/stock/reservations/" + orderId + "/commit";
		CATALOG.server().stubFor(post(urlEqualTo(commitPath))
			.willReturn(okJson("""
					{"orderId":"%s","status":"committed","expiresAt":"2026-10-05T12:00:00Z"}
					""".formatted(orderId))));

		this.job.executeRound();

		assertThat(output.getAll()).contains("Stock sync round completed: processed=1, counts=[committed=1]");
		assertThat(output.getAll()).doesNotContain(orderId.toString());
		assertThat(output.getAll()).doesNotContain("10.00");
	}

	@Test
	void explainVerifiesIndexUsage() {
		// En az 1 satır olsun ki optimizer tabloyu okumayı seçebilsin
		insertOrder("paid", "held", Duration.ofSeconds(20));

		List<Map<String, Object>> plan = this.jdbc.queryForList("""
				EXPLAIN SELECT o.id, o.status, o.stock_state
				FROM orders o
				WHERE ((o.status = 'paid' AND o.stock_state = 'held')
				    OR (o.status = 'failed' AND o.stock_state IN ('requested', 'held')))
				  AND o.updated_at < NOW()
				ORDER BY o.updated_at ASC
				LIMIT 50
				""");

		assertThat(plan).isNotEmpty();
		String possibleKeys = (String) plan.get(0).get("possible_keys");
		String key = (String) plan.get(0).get("key");

		// MySQL optimizer ix_orders_stock_state_updated indeksini olası anahtarlar arasında veya seçilen anahtar olarak görür
		assertThat(possibleKeys != null && possibleKeys.contains("ix_orders_stock_state_updated")
				|| "ix_orders_stock_state_updated".equals(key)).isTrue();
	}

	private static final java.time.format.DateTimeFormatter UTC_FORMATTER =
			java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(java.time.ZoneOffset.UTC);

	private UUID insertOrder(String status, String stockState, Duration age) {
		UUID userId = UUID.randomUUID();
		UUID cartId = UUID.randomUUID();
		UUID paymentId = "paid".equals(status) ? UUID.randomUUID() : null;
		String failureCode = "failed".equals(status) ? "ORDER_EXPIRED" : null;
		Instant past = this.clock.instant().minus(age);

		Order order = Order.place(userId, cartId, "TRY",
				List.of(new OrderLine(UUID.randomUUID(), "Book", 1, new BigDecimal("10.00"))),
				new AddressSnapshot("User", "5550000000", "Street", null, null, "Ankara", null, "TR"),
				this.clock);
		UUID orderId = this.transactions.insert(order).id();

		int updated = this.jdbc.update("""
				UPDATE orders
				SET status = ?, stock_state = ?, payment_id = UUID_TO_BIN(?), failure_code = ?, updated_at = ?
				WHERE id = UUID_TO_BIN(?)
				""", status, stockState, paymentId != null ? paymentId.toString() : null,
				failureCode, UTC_FORMATTER.format(past), orderId.toString());
		assertThat(updated).isEqualTo(1);

		return orderId;
	}

	private static byte[] uuidToBytes(UUID uuid) {
		java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(new byte[16]);
		bb.putLong(uuid.getMostSignificantBits());
		bb.putLong(uuid.getLeastSignificantBits());
		return bb.array();
	}

	private String stockState(UUID orderId) {
		return this.jdbc.queryForObject("SELECT stock_state FROM orders WHERE id = UUID_TO_BIN(?)", String.class,
				orderId.toString());
	}

}
