package com.kitapsepeti.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kitapsepeti.common.error.DbConstraints;
import com.kitapsepeti.order.TestcontainersConfiguration;
import com.kitapsepeti.order.entity.AddressSnapshot;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderItem;
import com.kitapsepeti.order.entity.OrderLine;
import com.kitapsepeti.order.entity.OrderReasons;
import com.kitapsepeti.order.entity.OrderStatus;
import com.kitapsepeti.order.entity.OrderStatusHistory;
import com.kitapsepeti.order.entity.StockState;
import com.kitapsepeti.order.entity.TransitionResult;
import com.kitapsepeti.order.support.SqlCapture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sipariş aggregate'inin eşlemesi ve repository sorguları (gerçek MySQL, Flyway V1, ddl-auto validate). Her test kendi
 * transaction'ında koşar ve geri alınır; DB'deki değer aynı transaction'da JDBC ile okunur. Kilit davranışı
 * {@link OrderLockingTest}'te.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
		+ "com.kitapsepeti.order.support.SqlCapture")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderRepositoryTest {

	private static final Clock T1 = Clock.fixed(Instant.parse("2026-03-01T10:15:30.123456789Z"), ZoneOffset.UTC);

	private static final Clock T2 = Clock.fixed(Instant.parse("2026-03-01T10:16:00.000001Z"), ZoneOffset.UTC);

	private static final Clock T3 = Clock.fixed(Instant.parse("2026-03-01T10:17:00.000002Z"), ZoneOffset.UTC);

	private static final AddressSnapshot MINIMAL_ADDRESS = new AddressSnapshot("Alıcı Ad", "+905550000000",
			"Sokak 1", null, null, "Ankara", null, "TR");

	@Autowired
	private OrderRepository orders;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	private final UUID userId = UUID.randomUUID();

	private final UUID cartId = UUID.randomUUID();

	@Test
	void contextStartsWithSchemaValidation() {
		Object ddlAuto = entityManager.getEntityManagerFactory().getProperties().get("hibernate.hbm2ddl.auto");

		assertThat(ddlAuto).isEqualTo("validate");
	}

	@Test
	void placedOrderRoundTripsInANewPersistenceContext() {
		OrderLine first = new OrderLine(UUID.randomUUID(), "Birinci Kitap", 2, new BigDecimal("10.5"));
		OrderLine free = new OrderLine(UUID.randomUUID(), "Ücretsiz Kitap", 1, BigDecimal.ZERO);
		Order saved = orders.saveAndFlush(Order.place(userId, cartId, "TRY", List.of(first, free), MINIMAL_ADDRESS, T1));
		entityManager.clear();

		Order loaded = orders.findById(saved.getId()).orElseThrow();

		assertThat(loaded).isNotSameAs(saved);
		assertThat(loaded.getId().version()).isEqualTo(7);
		assertThat(loaded.getUserId()).isEqualTo(userId);
		assertThat(loaded.getCartId()).isEqualTo(cartId);
		assertThat(loaded.getStatus()).isEqualTo(OrderStatus.PENDING);
		assertThat(loaded.getStockState()).isEqualTo(StockState.REQUESTED);
		assertThat(loaded.getCurrency()).isEqualTo("TRY");
		assertThat(loaded.getSubtotal()).isEqualTo(new BigDecimal("21.00"));
		assertThat(loaded.getDiscountAmount()).isEqualTo(new BigDecimal("0.00"));
		assertThat(loaded.getTotalAmount()).isEqualTo(new BigDecimal("21.00"));
		assertThat(loaded.getCouponCode()).isNull();
		assertThat(loaded.getPaymentId()).isNull();
		assertThat(loaded.getFailureCode()).isNull();
		assertThat(loaded.getAddressSnapshot()).isEqualTo(MINIMAL_ADDRESS);
		assertThat(loaded.getAddressSnapshot().line2()).isNull();
		assertThat(loaded.getCreatedAt()).isEqualTo(Instant.parse("2026-03-01T10:15:30.123456Z"));
		assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
		assertThat(loaded.getItems())
			.extracting(OrderItem::getId, OrderItem::getBookId, OrderItem::getTitleSnapshot, OrderItem::getQuantity,
					OrderItem::getUnitPrice, OrderItem::getLineTotal)
			.containsExactlyElementsOf(saved.getItems()
				.stream()
				.map(i -> tuple(i.getId(), i.getBookId(), i.getTitleSnapshot(), i.getQuantity(), i.getUnitPrice(),
						i.getLineTotal()))
				.toList());
		assertThat(loaded.getItems()).extracting(OrderItem::getBookId).containsExactly(first.bookId(), free.bookId());
		assertThat(loaded.getHistory())
			.extracting(OrderStatusHistory::getId, OrderStatusHistory::getFromStatus, OrderStatusHistory::getToStatus,
					OrderStatusHistory::getReason, OrderStatusHistory::getCreatedAt)
			.containsExactly(tuple(saved.getHistory().get(0).getId(), null, OrderStatus.PENDING,
					OrderReasons.ORDER_PLACED, loaded.getCreatedAt()));
	}

	@Test
	void columnsAreStoredLowerCaseWithScaleTwoAndAddressAsJsonObject() {
		Order order = orders.saveAndFlush(place(new BigDecimal("149.9")));

		Map<String, Object> row = jdbc.queryForMap("""
				SELECT status AS s, stock_state AS st, currency AS c, CAST(subtotal AS CHAR) AS sub,
					CAST(discount_amount AS CHAR) AS d, CAST(total_amount AS CHAR) AS t, coupon_code AS cc,
					payment_id AS p, failure_code AS f, JSON_TYPE(address_snapshot) AS jt,
					address_snapshot->>'$.city' AS city, JSON_TYPE(address_snapshot->'$.line2') AS line2,
					JSON_LENGTH(address_snapshot) AS fields, BIN_TO_UUID(active_pending_user_id) AS pending,
					DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created
				FROM orders WHERE id = ?
				""", (Object) bytes(order.getId()));
		assertThat(row).containsEntry("s", "pending")
			.containsEntry("st", "requested")
			.containsEntry("c", "TRY")
			.containsEntry("sub", "149.90")
			.containsEntry("d", "0.00")
			.containsEntry("t", "149.90")
			.containsEntry("cc", null)
			.containsEntry("p", null)
			.containsEntry("f", null)
			.containsEntry("jt", "OBJECT")
			.containsEntry("city", "Ankara")
			.containsEntry("line2", "NULL")
			.containsEntry("pending", userId.toString())
			.containsEntry("created", "2026-03-01 10:15:30.123456");
		assertThat(((Number) row.get("fields")).intValue()).isEqualTo(8);
		Map<String, Object> history = jdbc.queryForMap(
				"SELECT from_status AS f, to_status AS t, reason AS r FROM order_status_history WHERE order_id = ?",
				(Object) bytes(order.getId()));
		assertThat(history).containsEntry("f", null).containsEntry("t", "pending").containsEntry("r", "ORDER_PLACED");
	}

	/** DB default'larına güvenilmez: para birimi, indirim ve durumlar INSERT'te açıkça yazılır; generated kolon yazılmaz. */
	@Test
	void insertSendsCurrencyDiscountAndStatesExplicitly() {
		SqlCapture.start();
		orders.saveAndFlush(place(BigDecimal.TEN));
		List<String> statements = SqlCapture.stop();

		assertThat(statements).filteredOn(sql -> sql.startsWith("insert into orders")).singleElement()
			.satisfies(sql -> assertThat(sql).contains("currency", "discount_amount", "status", "stock_state",
					"address_snapshot")
				.doesNotContain("active_pending_user_id"));
		assertThat(statements).filteredOn(sql -> sql.startsWith("insert into order_items")).hasSize(1);
		assertThat(statements).filteredOn(sql -> sql.startsWith("insert into order_status_history")).hasSize(1);
	}

	@Test
	void secondPendingOrderForSameUserViolatesUkOrdersPendingUser() {
		orders.saveAndFlush(place(BigDecimal.TEN));

		Throwable thrown = catchThrowable(() -> orders.saveAndFlush(place(BigDecimal.ONE)));

		assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(DbConstraints.isViolated(thrown, "uk_orders_pending_user")).isTrue();
	}

	@Test
	void newPendingOrderIsAllowedAfterPreviousFailed() {
		Order first = orders.saveAndFlush(place(BigDecimal.TEN));
		first.markFailed(OrderReasons.OUT_OF_STOCK, T2);
		orders.flush();

		Order second = orders.saveAndFlush(place(BigDecimal.ONE));

		assertThat(orders.findPendingByUserId(userId)).get().extracting(Order::getId).isEqualTo(second.getId());
	}

	@Test
	void paidAndCommittedOrderPassesDbConstraints() {
		Order order = orders.saveAndFlush(place(BigDecimal.TEN));
		UUID paymentId = UUID.randomUUID();

		assertThat(order.markStockHeld(T2)).isEqualTo(TransitionResult.APPLIED);
		assertThat(order.markPaid(paymentId, T2)).isEqualTo(TransitionResult.APPLIED);
		orders.flush();
		assertThat(dbRow(order.getId())).containsEntry("s", "paid")
			.containsEntry("st", "held")
			.containsEntry("p", paymentId.toString())
			.containsEntry("f", null)
			.containsEntry("updated", "2026-03-01 10:16:00.000001");
		assertThat(historyRows(order.getId())).containsExactly(tuple(null, "pending", "ORDER_PLACED"),
				tuple("pending", "paid", "PAYMENT_SUCCEEDED"));

		assertThat(order.markStockCommitted(T3)).isEqualTo(TransitionResult.APPLIED);
		orders.flush();
		assertThat(dbRow(order.getId())).containsEntry("s", "paid")
			.containsEntry("st", "committed")
			.containsEntry("updated", "2026-03-01 10:17:00.000002");
		assertThat(historyRows(order.getId())).hasSize(2);
	}

	@Test
	void failedAndReleasedOrderPassesDbConstraints() {
		Order order = orders.saveAndFlush(place(BigDecimal.TEN));
		order.markStockHeld(T2);

		assertThat(order.markFailed(OrderReasons.CARD_DECLINED, T2)).isEqualTo(TransitionResult.APPLIED);
		orders.flush();
		assertThat(dbRow(order.getId())).containsEntry("s", "failed")
			.containsEntry("st", "held")
			.containsEntry("f", "CARD_DECLINED")
			.containsEntry("pending", null);
		assertThat(historyRows(order.getId())).containsExactly(tuple(null, "pending", "ORDER_PLACED"),
				tuple("pending", "failed", "CARD_DECLINED"));

		assertThat(order.markStockReleased(T3)).isEqualTo(TransitionResult.APPLIED);
		assertThat(order.markFailed(OrderReasons.ORDER_EXPIRED, T3)).isEqualTo(TransitionResult.ALREADY_IN_STATE);
		orders.flush();
		assertThat(dbRow(order.getId())).containsEntry("st", "released").containsEntry("f", "CARD_DECLINED");
		assertThat(historyRows(order.getId())).hasSize(2);
	}

	@Test
	void historyIsReadBackInCreationOrder() {
		Order order = orders.saveAndFlush(place(BigDecimal.TEN));
		order.markStockHeld(T2);
		order.markPaid(UUID.randomUUID(), T3);
		orders.flush();
		entityManager.clear();

		Order loaded = orders.findById(order.getId()).orElseThrow();

		assertThat(loaded.getHistory()).extracting(OrderStatusHistory::getToStatus)
			.containsExactly(OrderStatus.PENDING, OrderStatus.PAID);
	}

	@Test
	void findByIdAndUserIdChecksOwnerAndFetchesItems() {
		Order order = orders.saveAndFlush(place(BigDecimal.TEN));
		entityManager.clear();

		Order own = orders.findByIdAndUserId(order.getId(), userId).orElseThrow();

		assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(own, "items")).isTrue();
		assertThat(own.getItems()).hasSize(1);
		assertThat(orders.findByIdAndUserId(order.getId(), UUID.randomUUID())).isEmpty();
		assertThat(orders.findByIdAndUserId(UUID.randomUUID(), userId)).isEmpty();
	}

	@Test
	void findPendingByUserIdReturnsOnlyThePendingOrder() {
		Order order = orders.saveAndFlush(place(BigDecimal.TEN));

		assertThat(orders.findPendingByUserId(userId)).get().extracting(Order::getId).isEqualTo(order.getId());
		assertThat(orders.findPendingByUserId(UUID.randomUUID())).isEmpty();

		order.markFailed(OrderReasons.ORDER_EXPIRED, T2);
		orders.flush();
		assertThat(orders.findPendingByUserId(userId)).isEmpty();
	}

	@Test
	void findByIdForUpdateIssuesSelectForUpdate() {
		Order order = orders.saveAndFlush(place(BigDecimal.TEN));
		entityManager.clear();

		SqlCapture.start();
		Order locked = orders.findByIdForUpdate(order.getId()).orElseThrow();
		List<String> statements = SqlCapture.stop();

		assertThat(locked.getId()).isEqualTo(order.getId());
		assertThat(statements).anySatisfy(sql -> assertThat(sql).contains("from orders", "for update"));
		assertThat(orders.findByIdForUpdate(UUID.randomUUID())).isEmpty();
	}

	/** Sabit JSON sözleşmesi: DB'ye elle eklenmiş bilinmeyen alan okunurken hata verir, sessizce atlanmaz. */
	@Test
	void unknownFieldInStoredAddressFailsOnRead() {
		Order order = orders.saveAndFlush(place(BigDecimal.TEN));
		jdbc.update("UPDATE orders SET address_snapshot = JSON_SET(address_snapshot, '$.label', 'Ev') WHERE id = ?",
				(Object) bytes(order.getId()));
		entityManager.clear();

		Throwable thrown = catchThrowable(() -> orders.findById(order.getId()));

		assertThat(thrown).isNotNull();
		assertThat(causeChain(thrown)).anySatisfy(cause -> assertThat(cause)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("Stored address snapshot must have exactly the fields"));
	}

	@Test
	void missingFieldInStoredAddressFailsOnRead() {
		Order order = orders.saveAndFlush(place(BigDecimal.TEN));
		jdbc.update("UPDATE orders SET address_snapshot = JSON_REMOVE(address_snapshot, '$.line2') WHERE id = ?",
				(Object) bytes(order.getId()));
		entityManager.clear();

		Throwable thrown = catchThrowable(() -> orders.findById(order.getId()));

		assertThat(causeChain(thrown)).anySatisfy(cause -> assertThat(cause)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("Stored address snapshot must have exactly the fields"));
	}

	private Order place(BigDecimal unitPrice) {
		return Order.place(userId, cartId, "TRY",
				List.of(new OrderLine(UUID.randomUUID(), "Kitap", 1, unitPrice)), MINIMAL_ADDRESS, T1);
	}

	private Map<String, Object> dbRow(UUID id) {
		return jdbc.queryForMap("""
				SELECT status AS s, stock_state AS st, BIN_TO_UUID(payment_id) AS p, failure_code AS f,
					BIN_TO_UUID(active_pending_user_id) AS pending,
					DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') AS updated
				FROM orders WHERE id = ?
				""", (Object) bytes(id));
	}

	private List<org.assertj.core.groups.Tuple> historyRows(UUID orderId) {
		return jdbc.query("""
				SELECT from_status, to_status, reason FROM order_status_history
				WHERE order_id = ? ORDER BY created_at, id
				""", (rs, n) -> tuple(rs.getString(1), rs.getString(2), rs.getString(3)), (Object) bytes(orderId));
	}

	private static List<Throwable> causeChain(Throwable thrown) {
		List<Throwable> chain = new java.util.ArrayList<>();
		for (Throwable t = thrown; t != null && !chain.contains(t); t = t.getCause()) {
			chain.add(t);
		}
		return chain;
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16)
			.putLong(id.getMostSignificantBits())
			.putLong(id.getLeastSignificantBits())
			.array();
	}

}
