package com.kitapsepeti.order.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kitapsepeti.order.TestcontainersConfiguration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * V1 şemasının yapısını ve kısıtlarının DB seviyesinde uygulandığını doğrular (entity yok, düz SQL).
 * Her test kendi transaction'ında koşar ve geri alınır.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderSchemaConstraintsTest {

	private static final int MYSQL_CHECK_CONSTRAINT_VIOLATED = 3819;

	private static final String NOW = "2026-01-01 10:00:00.000000";

	private static final String ADDRESS = """
			{"fullName":"Ad Soyad","phone":"5550000000","city":"İstanbul","district":"Kadıköy","line":"Adres satırı"}""";

	private static final Path USER_V1 = Path.of("..", "user-service", "src", "main", "resources", "db", "migration",
			"V1__create_user_tables.sql");

	@Autowired
	private JdbcTemplate jdbc;

	// --- yapı ---

	@Test
	void flywayAppliesAllMigrationsAndCreatesOnlyOrderTables() {
		List<String> applied = jdbc.queryForList(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);
		List<Map<String, Object>> tables = jdbc.queryForList("""
				SELECT table_name AS name, engine AS eng, table_collation AS collation FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
				""");

		assertThat(applied).containsExactly("1", "2", "3");
		// Takma adlar küçük harf: satır map'i anahtarı JVM locale'iyle küçültür (tr-TR'de "ENGINE" → "engıne").
		assertThat(tables).extracting(t -> t.get("name"), t -> t.get("eng"), t -> t.get("collation"))
			.containsExactlyInAnyOrder(tuple("orders", "InnoDB", "utf8mb4_0900_ai_ci"),
					tuple("order_items", "InnoDB", "utf8mb4_0900_ai_ci"),
					tuple("order_status_history", "InnoDB", "utf8mb4_0900_ai_ci"),
					tuple("outbox", "InnoDB", "utf8mb4_0900_ai_ci"));
	}

	@Test
	void ordersColumnsMatchV1() {
		assertThat(columns("orders")).containsExactly(
				tuple("id", "binary(16)", "NO", null, ""),
				tuple("user_id", "binary(16)", "NO", null, ""),
				tuple("cart_id", "binary(16)", "NO", null, ""),
				tuple("status", "varchar(16)", "NO", "pending", ""),
				tuple("stock_state", "varchar(16)", "NO", "requested", ""),
				tuple("currency", "char(3)", "NO", "TRY", ""),
				tuple("subtotal", "decimal(12,2)", "NO", null, ""),
				tuple("discount_amount", "decimal(12,2)", "NO", "0.00", ""),
				tuple("total_amount", "decimal(12,2)", "NO", null, ""),
				tuple("coupon_code", "varchar(40)", "YES", null, ""),
				tuple("address_snapshot", "json", "NO", null, ""),
				tuple("payment_id", "binary(16)", "YES", null, ""),
				tuple("failure_code", "varchar(64)", "YES", null, ""),
				tuple("late_payment_at", "datetime(6)", "YES", null, ""),
				tuple("active_pending_user_id", "binary(16)", "YES", null, "VIRTUAL GENERATED"),
				tuple("created_at", "datetime(6)", "NO", null, ""),
				tuple("updated_at", "datetime(6)", "NO", null, ""));
	}

	@Test
	void orderItemsColumnsMatchV1() {
		assertThat(columns("order_items")).containsExactly(
				tuple("id", "binary(16)", "NO", null, ""),
				tuple("order_id", "binary(16)", "NO", null, ""),
				tuple("book_id", "binary(16)", "NO", null, ""),
				tuple("title_snapshot", "varchar(300)", "NO", null, ""),
				tuple("quantity", "int", "NO", null, ""),
				tuple("unit_price", "decimal(12,2)", "NO", null, ""),
				tuple("line_total", "decimal(12,2)", "NO", null, ""));
	}

	@Test
	void orderStatusHistoryColumnsMatchV1() {
		assertThat(columns("order_status_history")).containsExactly(
				tuple("id", "binary(16)", "NO", null, ""),
				tuple("order_id", "binary(16)", "NO", null, ""),
				tuple("from_status", "varchar(16)", "YES", null, ""),
				tuple("to_status", "varchar(16)", "NO", null, ""),
				tuple("reason", "varchar(64)", "YES", null, ""),
				tuple("created_at", "datetime(6)", "NO", null, ""));
	}

	/** Durum, makine kodu ve para birimi kolonları {@code utf8mb4_bin}; serbest metin (başlık) ve outbox varsayılan. */
	@Test
	void enumLikeAndCodeColumnsUseBinaryCollation() {
		List<Map<String, Object>> collations = jdbc.queryForList("""
				SELECT table_name AS t, column_name AS c, collation_name AS coll FROM information_schema.columns
				WHERE table_schema = DATABASE()
				AND table_name IN ('orders', 'order_items', 'order_status_history', 'outbox')
				AND collation_name IS NOT NULL
				""");

		assertThat(collations).extracting(c -> c.get("t"), c -> c.get("c"), c -> c.get("coll"))
			.containsExactlyInAnyOrder(
					tuple("orders", "status", "utf8mb4_bin"),
					tuple("orders", "stock_state", "utf8mb4_bin"),
					tuple("orders", "currency", "utf8mb4_bin"),
					tuple("orders", "coupon_code", "utf8mb4_bin"),
					tuple("orders", "failure_code", "utf8mb4_bin"),
					tuple("order_items", "title_snapshot", "utf8mb4_0900_ai_ci"),
					tuple("order_status_history", "from_status", "utf8mb4_bin"),
					tuple("order_status_history", "to_status", "utf8mb4_bin"),
					tuple("order_status_history", "reason", "utf8mb4_bin"),
					tuple("outbox", "aggregate_type", "utf8mb4_0900_ai_ci"),
					tuple("outbox", "event_type", "utf8mb4_0900_ai_ci"));
	}

	@Test
	void constraintsAndIndexesHaveExplicitNames() {
		List<Map<String, Object>> constraints = jdbc.queryForList("""
				SELECT table_name AS t, constraint_name AS n, constraint_type AS k FROM information_schema.table_constraints
				WHERE table_schema = DATABASE()
				AND table_name IN ('orders', 'order_items', 'order_status_history', 'outbox')
				""");
		// MySQL birincil anahtarı her zaman PRIMARY adıyla saklar (V1'deki pk_* adı yok sayılır).
		assertThat(constraints).extracting(c -> c.get("t"), c -> c.get("n"), c -> c.get("k"))
			.containsExactlyInAnyOrder(
					tuple("orders", "PRIMARY", "PRIMARY KEY"),
					tuple("orders", "uk_orders_pending_user", "UNIQUE"),
					tuple("orders", "uk_orders_payment", "UNIQUE"),
					tuple("orders", "ck_orders_status", "CHECK"),
					tuple("orders", "ck_orders_stock_state", "CHECK"),
					tuple("orders", "ck_orders_currency", "CHECK"),
					tuple("orders", "ck_orders_subtotal", "CHECK"),
					tuple("orders", "ck_orders_discount", "CHECK"),
					tuple("orders", "ck_orders_total", "CHECK"),
					tuple("orders", "ck_orders_address_snapshot", "CHECK"),
					tuple("orders", "ck_orders_failure_code", "CHECK"),
					tuple("orders", "ck_orders_failure", "CHECK"),
					tuple("orders", "ck_orders_paid_payment", "CHECK"),
					tuple("orders", "ck_orders_pending_stock", "CHECK"),
					tuple("orders", "ck_orders_paid_stock", "CHECK"),
					tuple("orders", "ck_orders_committed_paid", "CHECK"),
					tuple("orders", "ck_orders_lost_paid", "CHECK"),
					tuple("orders", "ck_orders_late_payment_failed", "CHECK"),
					tuple("order_items", "PRIMARY", "PRIMARY KEY"),
					tuple("order_items", "uk_order_items_order_book", "UNIQUE"),
					tuple("order_items", "fk_order_items_order", "FOREIGN KEY"),
					tuple("order_items", "ck_order_items_quantity", "CHECK"),
					tuple("order_items", "ck_order_items_unit_price", "CHECK"),
					tuple("order_items", "ck_order_items_line_total", "CHECK"),
					tuple("order_status_history", "PRIMARY", "PRIMARY KEY"),
					tuple("order_status_history", "fk_order_status_history_order", "FOREIGN KEY"),
					tuple("order_status_history", "ck_order_status_history_from", "CHECK"),
					tuple("order_status_history", "ck_order_status_history_to", "CHECK"),
					tuple("order_status_history", "ck_order_status_history_reason", "CHECK"),
					tuple("order_status_history", "ck_order_status_history_initial", "CHECK"),
					tuple("order_status_history", "ck_order_status_history_change", "CHECK"),
					tuple("outbox", "PRIMARY", "PRIMARY KEY"));

		List<Map<String, Object>> fks = jdbc.queryForList("""
				SELECT constraint_name AS n, referenced_table_name AS ref, delete_rule AS del
				FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE()
				""");
		assertThat(fks).extracting(f -> f.get("n"), f -> f.get("ref"), f -> f.get("del"))
			.containsExactlyInAnyOrder(tuple("fk_order_items_order", "orders", "RESTRICT"),
					tuple("fk_order_status_history_order", "orders", "RESTRICT"));

		List<Map<String, Object>> indexes = jdbc.queryForList("""
				SELECT table_name AS t, index_name AS i, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
				FROM information_schema.statistics
				WHERE table_schema = DATABASE()
				AND table_name IN ('orders', 'order_items', 'order_status_history', 'outbox')
				GROUP BY table_name, index_name
				""");
		// FK için ayrı (fk_* adlı) indeks oluşmaz; order_id ile başlayan UNIQUE / ix_* kullanılır. cart_id indeksi yok.
		assertThat(indexes).extracting(i -> i.get("t"), i -> i.get("i"), i -> i.get("cols"))
			.containsExactlyInAnyOrder(
					tuple("orders", "PRIMARY", "id"),
					tuple("orders", "uk_orders_pending_user", "active_pending_user_id"),
					tuple("orders", "uk_orders_payment", "payment_id"),
					tuple("orders", "ix_orders_user_created", "user_id,created_at,id"),
					tuple("orders", "ix_orders_status_created", "status,created_at"),
					tuple("orders", "ix_orders_stock_state_updated", "stock_state,updated_at"),
					tuple("order_items", "PRIMARY", "id"),
					tuple("order_items", "uk_order_items_order_book", "order_id,book_id"),
					tuple("order_status_history", "PRIMARY", "id"),
					tuple("order_status_history", "ix_order_status_history_order_created", "order_id,created_at,id"),
					tuple("outbox", "PRIMARY", "id"),
					tuple("outbox", "ix_outbox_published_at_created_at", "published_at,created_at"));
	}

	// --- orders: kullanıcı başına tek bekleyen sipariş ---

	@Test
	void rejectsSecondPendingOrderForSameUser() {
		byte[] userId = newId();
		order().user(userId).insert();

		assertDuplicate(() -> order().user(userId).insert(), "uk_orders_pending_user");
	}

	@ParameterizedTest
	@ValueSource(strings = { "paid", "failed" })
	void newPendingOrderIsAcceptedOnceThePreviousOneIsSettled(String settledStatus) {
		byte[] userId = newId();
		byte[] first = order().user(userId).insert();
		if (settledStatus.equals("paid")) {
			jdbc.update("UPDATE orders SET status = 'paid', stock_state = 'held', payment_id = ? WHERE id = ?", newId(),
					first);
		}
		else {
			jdbc.update("UPDATE orders SET status = 'failed', failure_code = 'ORDER_EXPIRED' WHERE id = ?",
					(Object) first);
		}

		order().user(userId).insert();

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, (Object) userId))
			.isEqualTo(2);
	}

	@Test
	void pendingOrdersOfDifferentUsersAreIndependent() {
		order().user(newId()).insert();
		order().user(newId()).insert();
	}

	@Test
	void settledOrdersOfSameUserAreUnlimited() {
		byte[] userId = newId();
		order().user(userId).status("paid").stock("committed").payment(newId()).insert();
		order().user(userId).status("paid").stock("held").payment(newId()).insert();
		order().user(userId).status("failed").stock("released").failure("PAYMENT_FAILED").insert();
		order().user(userId).status("failed").stock("released").failure("ORDER_EXPIRED").insert();
		order().user(userId).insert();
	}

	@Test
	void columnsHaveDefaults() {
		byte[] id = newId();
		jdbc.update("""
				INSERT INTO orders (id, user_id, cart_id, subtotal, total_amount, address_snapshot, created_at,
					updated_at)
				VALUES (?, ?, ?, 10.00, 10.00, ?, ?, ?)
				""", id, newId(), newId(), ADDRESS, NOW, NOW);

		Map<String, Object> row = jdbc.queryForMap("""
				SELECT status AS s, stock_state AS st, currency AS c, discount_amount AS d FROM orders WHERE id = ?
				""", (Object) id);
		assertThat(row).containsEntry("s", "pending").containsEntry("st", "requested").containsEntry("c", "TRY");
		assertThat((BigDecimal) row.get("d")).isEqualByComparingTo("0");
	}

	// --- orders: durum ve stok durumu ---

	@ParameterizedTest
	@ValueSource(strings = { "PENDING", "Paid", "cancelled", "" })
	void rejectsUnknownOrMiscasedStatus(String status) {
		assertCheckViolation(() -> order().status(status).insert(), "ck_orders_status");
	}

	/** failed + kod: diğer durum/stok kuralları bu değerlerden etkilenmez, yalnızca ck_orders_stock_state düşer. */
	@ParameterizedTest
	@ValueSource(strings = { "REQUESTED", "Held", "reserved", "" })
	void rejectsUnknownOrMiscasedStockState(String stockState) {
		assertCheckViolation(() -> order().status("failed").failure("PAYMENT_FAILED").stock(stockState).insert(),
				"ck_orders_stock_state");
	}

	@Test
	void failedOrderRequiresFailureCode() {
		assertCheckViolation(() -> order().status("failed").stock("released").insert(), "ck_orders_failure");
	}

	@Test
	void pendingOrderMustNotHaveFailureCode() {
		assertCheckViolation(() -> order().failure("PAYMENT_FAILED").insert(), "ck_orders_failure");
	}

	@Test
	void paidOrderMustNotHaveFailureCode() {
		assertCheckViolation(
				() -> order().status("paid").stock("held").payment(newId()).failure("PAYMENT_FAILED").insert(),
				"ck_orders_failure");
	}

	@Test
	void failedOrderWithCodeIsAccepted() {
		byte[] id = order().status("failed").stock("released").failure("ORDER_EXPIRED").insert();

		assertThat(jdbc.queryForObject("SELECT failure_code FROM orders WHERE id = ?", String.class, (Object) id))
			.isEqualTo("ORDER_EXPIRED");
	}

	@ParameterizedTest
	@ValueSource(strings = { "order_expired", "Order_Expired", "ORDER EXPIRED", "1ORDER", "_ORDER", "ORDER-EXPIRED", "" })
	void rejectsFailureCodeThatIsNotUpperSnakeCase(String code) {
		assertCheckViolation(() -> order().status("failed").stock("released").failure(code).insert(),
				"ck_orders_failure_code");
	}

	@ParameterizedTest
	@ValueSource(strings = { "ORDER_EXPIRED", "PAYMENT_FAILED", "STOCK_UNAVAILABLE", "E1" })
	void acceptsUpperSnakeCaseFailureCode(String code) {
		order().status("failed").stock("released").failure(code).insert();
	}

	@Test
	void paidOrderRequiresPaymentId() {
		assertCheckViolation(() -> order().status("paid").stock("held").insert(), "ck_orders_paid_payment");
	}

	@Test
	void pendingOrderMayAlreadyHavePaymentId() {
		order().payment(newId()).insert();
	}

	@Test
	void paymentBelongsToSingleOrder() {
		byte[] paymentId = newId();
		order().status("paid").stock("held").payment(paymentId).insert();

		assertDuplicate(() -> order().status("paid").stock("held").payment(paymentId).insert(), "uk_orders_payment");
	}

	@ParameterizedTest
	@CsvSource({ "pending, released, ck_orders_pending_stock", "paid, requested, ck_orders_paid_stock",
			"paid, released, ck_orders_paid_stock", "failed, committed, ck_orders_committed_paid" })
	void rejectsInconsistentStatusAndStockState(String status, String stockState, String constraint) {
		assertCheckViolation(() -> settledOrder(status, stockState).insert(), constraint);
	}

	/** İki kuralı birden ihlal eder; MySQL ilk düşen kısıtı bildirir. */
	@Test
	void rejectsPendingOrderWithCommittedStock() {
		assertCheckViolation(() -> order().stock("committed").insert(), "ck_orders_pending_stock",
				"ck_orders_committed_paid");
	}

	@ParameterizedTest
	@CsvSource({ "pending, requested", "pending, held", "paid, held", "paid, committed", "paid, lost", "failed, requested",
			"failed, held", "failed, released" })
	void acceptsConsistentStatusAndStockState(String status, String stockState) {
		settledOrder(status, stockState).insert();
	}

	@ParameterizedTest
	@CsvSource({
			"pending, lost",
			"failed, lost"
	})
	void rejectsLostStockWhenNotPaid(String status, String stockState) {
		assertCheckViolation(() -> settledOrder(status, stockState).insert(), "ck_orders_lost_paid", "ck_orders_pending_stock");
	}

	@Test
	void committingStockOfFailedOrderIsRejectedOnUpdate() {
		byte[] id = order().status("failed").stock("held").failure("ORDER_EXPIRED").insert();

		assertCheckViolation(() -> jdbc.update("UPDATE orders SET stock_state = 'committed' WHERE id = ?", (Object) id),
				"ck_orders_committed_paid");
		jdbc.update("UPDATE orders SET stock_state = 'released' WHERE id = ?", (Object) id);
	}

	// --- orders: geç ödeme (V3) ---

	@Test
	void failedOrderWithLatePaymentIsAccepted() {
		byte[] id = order().status("failed").stock("released").failure("ORDER_EXPIRED").payment(newId())
			.latePayment(NOW)
			.insert();

		assertThat(jdbc.queryForObject("SELECT late_payment_at IS NOT NULL FROM orders WHERE id = ?", Boolean.class,
				(Object) id))
			.isTrue();
	}

	@ParameterizedTest
	@CsvSource({ "pending, held", "paid, held", "paid, committed" })
	void latePaymentIsRejectedUnlessFailed(String status, String stockState) {
		assertCheckViolation(() -> settledOrder(status, stockState).latePayment(NOW).insert(),
				"ck_orders_late_payment_failed");
	}

	@Test
	void latePaymentCannotSurviveLeavingFailed() {
		byte[] id = order().status("failed").stock("held").failure("ORDER_EXPIRED").latePayment(NOW).insert();

		assertCheckViolation(() -> jdbc.update(
				"UPDATE orders SET status = 'paid', failure_code = NULL, payment_id = ? WHERE id = ?", newId(), id),
				"ck_orders_late_payment_failed");
	}

	// --- orders: tutarlar ---

	@ParameterizedTest
	@CsvSource({ "100.00, 0.00, 100.00", "100.00, 10.00, 90.00", "0.01, 0.00, 0.01", "100.00, 99.99, 0.01" })
	void acceptsConsistentAmounts(String subtotal, String discount, String total) {
		order().amounts(subtotal, discount, total).insert();
	}

	@ParameterizedTest
	@CsvSource({ "100.00, 10.00, 100.00", "100.00, 0.00, 99.99", "100.00, 10.00, 110.00" })
	void rejectsTotalThatIsNotSubtotalMinusDiscount(String subtotal, String discount, String total) {
		assertCheckViolation(() -> order().amounts(subtotal, discount, total).insert(), "ck_orders_total");
	}

	@Test
	void rejectsZeroTotal() {
		assertCheckViolation(() -> order().amounts("100.00", "100.00", "0.00").insert(), "ck_orders_total");
	}

	@Test
	void rejectsNegativeDiscount() {
		assertCheckViolation(() -> order().amounts("100.00", "-10.00", "110.00").insert(), "ck_orders_discount");
	}

	/** total = subtotal − discount tutsa da toplam negatif olur; ikisi de düşer, MySQL ilkini bildirir. */
	@Test
	void rejectsDiscountGreaterThanSubtotal() {
		assertCheckViolation(() -> order().amounts("100.00", "150.00", "-50.00").insert(), "ck_orders_discount",
				"ck_orders_total");
	}

	/** Catalog 0 fiyata izin verir; tamamı ücretsiz sepetin siparişi (ara toplam 0) şemada reddedilir. */
	@Test
	void rejectsAllFreeOrder() {
		assertCheckViolation(() -> order().amounts("0.00", "0.00", "0.00").insert(), "ck_orders_subtotal",
				"ck_orders_total");
	}

	@ParameterizedTest
	@ValueSource(strings = { "try", "Try", "TR", "T1Y" })
	void rejectsCurrencyThatIsNotThreeUpperCaseLetters(String currency) {
		assertCheckViolation(() -> order().currency(currency).insert(), "ck_orders_currency");
	}

	@Test
	void rejectsFourLetterCurrencyAsTooLong() {
		assertThatThrownBy(() -> order().currency("TRYY").insert())
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("Data too long for column 'currency'");
	}

	// --- orders: adres kopyası ---

	@ParameterizedTest
	@ValueSource(strings = { "[]", "[{\"city\":\"Ankara\"}]", "\"Ankara\"", "42", "null" })
	void rejectsAddressSnapshotThatIsNotAnObject(String json) {
		assertCheckViolation(() -> order().address(json).insert(), "ck_orders_address_snapshot");
	}

	@Test
	void rejectsInvalidJsonAddress() {
		assertThatThrownBy(() -> order().address("Kadıköy, İstanbul").insert()).hasMessageContaining("Invalid JSON text");
	}

	@Test
	void acceptsObjectAddressSnapshot() {
		byte[] id = order().insert();

		assertThat(jdbc.queryForObject("SELECT address_snapshot ->> '$.city' FROM orders WHERE id = ?", String.class,
				(Object) id))
			.isEqualTo("İstanbul");
	}

	// --- order_items ---

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 100 })
	void rejectsQuantityOutsideCartLimit(int quantity) {
		byte[] orderId = order().insert();

		assertCheckViolation(() -> insertItem(orderId, newId(), quantity, "10.00", lineTotal("10.00", quantity)),
				"ck_order_items_quantity");
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 99 })
	void acceptsQuantityWithinCartLimit(int quantity) {
		insertItem(order().insert(), newId(), quantity, "10.00", lineTotal("10.00", quantity));
	}

	@ParameterizedTest
	@CsvSource({ "2, 10.00, 10.00", "2, 10.00, 20.01", "3, 0.00, 0.01" })
	void rejectsLineTotalThatIsNotUnitPriceTimesQuantity(int quantity, String unitPrice, String lineTotal) {
		byte[] orderId = order().insert();

		assertCheckViolation(() -> insertItem(orderId, newId(), quantity, unitPrice, lineTotal),
				"ck_order_items_line_total");
	}

	@Test
	void rejectsNegativeUnitPrice() {
		byte[] orderId = order().insert();

		assertCheckViolation(() -> insertItem(orderId, newId(), 1, "-0.01", "-0.01"), "ck_order_items_unit_price");
	}

	/** Catalog ile aynı: ücretsiz kitap (0) kalem olarak geçerli; ara toplamın > 0 olması siparişin kuralı. */
	@Test
	void acceptsFreeItem() {
		insertItem(order().insert(), newId(), 2, "0.00", "0.00");
	}

	@Test
	void rejectsSameBookTwiceInOrder() {
		byte[] orderId = order().insert();
		byte[] bookId = newId();
		insertItem(orderId, bookId, 1, "10.00", "10.00");

		assertDuplicate(() -> insertItem(orderId, bookId, 2, "10.00", "20.00"), "uk_order_items_order_book");
	}

	@Test
	void sameBookInDifferentOrdersIsAccepted() {
		byte[] bookId = newId();
		insertItem(order().insert(), bookId, 1, "10.00", "10.00");
		insertItem(order().insert(), bookId, 1, "10.00", "10.00");
	}

	@Test
	void rejectsItemForMissingOrder() {
		assertThatThrownBy(() -> insertItem(newId(), newId(), 1, "10.00", "10.00"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("fk_order_items_order");
	}

	@Test
	void titleSnapshotFitsCatalogTitleLength() {
		insertItem(order().insert(), newId(), "a".repeat(300), 1, "10.00", "10.00");

		byte[] orderId = order().insert();
		assertThatThrownBy(() -> insertItem(orderId, newId(), "a".repeat(301), 1, "10.00", "10.00"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("Data too long for column 'title_snapshot'");
	}

	@Test
	void orderWithItemsCannotBeDeleted() {
		byte[] orderId = order().insert();
		insertItem(orderId, newId(), 1, "10.00", "10.00");

		assertThatThrownBy(() -> jdbc.update("DELETE FROM orders WHERE id = ?", (Object) orderId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("fk_order_items_order");
	}

	@Test
	void orderWithoutChildrenCanBeDeleted() {
		byte[] orderId = order().insert();

		assertThat(jdbc.update("DELETE FROM orders WHERE id = ?", (Object) orderId)).isEqualTo(1);
	}

	// --- order_status_history ---

	@Test
	void creationRecordFromNullToPendingIsAccepted() {
		byte[] orderId = order().insert();

		insertHistory(orderId, null, "pending", null);
	}

	@ParameterizedTest
	@ValueSource(strings = { "paid", "failed" })
	void creationRecordMustTargetPending(String toStatus) {
		byte[] orderId = order().insert();

		assertCheckViolation(() -> insertHistory(orderId, null, toStatus, null), "ck_order_status_history_initial");
	}

	@Test
	void rejectsPendingToPending() {
		byte[] orderId = order().insert();

		assertCheckViolation(() -> insertHistory(orderId, "pending", "pending", null),
				"ck_order_status_history_initial", "ck_order_status_history_change");
	}

	@ParameterizedTest
	@ValueSource(strings = { "paid", "failed" })
	void cannotReturnToPending(String fromStatus) {
		byte[] orderId = order().insert();

		assertCheckViolation(() -> insertHistory(orderId, fromStatus, "pending", null),
				"ck_order_status_history_initial");
	}

	@Test
	void rejectsSameStatusTransition() {
		byte[] orderId = order().insert();

		assertCheckViolation(() -> insertHistory(orderId, "paid", "paid", null), "ck_order_status_history_change");
	}

	@ParameterizedTest
	@CsvSource(value = { "pending, paid, NULL", "pending, failed, PAYMENT_FAILED", "pending, failed, ORDER_EXPIRED" },
			nullValues = "NULL")
	void acceptsTransitionsOutOfPending(String fromStatus, String toStatus, String reason) {
		byte[] orderId = order().insert();
		insertHistory(orderId, null, "pending", null);

		insertHistory(orderId, fromStatus, toStatus, reason);
	}

	@ParameterizedTest
	@ValueSource(strings = { "PAID", "Paid", "cancelled" })
	void rejectsUnknownOrMiscasedToStatus(String toStatus) {
		byte[] orderId = order().insert();

		assertCheckViolation(() -> insertHistory(orderId, "pending", toStatus, null), "ck_order_status_history_to");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Pending", "PENDING", "cancelled" })
	void rejectsUnknownOrMiscasedFromStatus(String fromStatus) {
		byte[] orderId = order().insert();

		assertCheckViolation(() -> insertHistory(orderId, fromStatus, "paid", null), "ck_order_status_history_from");
	}

	@ParameterizedTest
	@ValueSource(strings = { "payment_failed", "Payment_Failed", "PAYMENT FAILED", "9FAILED", "" })
	void rejectsReasonThatIsNotUpperSnakeCase(String reason) {
		byte[] orderId = order().insert();

		assertCheckViolation(() -> insertHistory(orderId, "pending", "failed", reason),
				"ck_order_status_history_reason");
	}

	@Test
	void orderWithHistoryCannotBeDeleted() {
		byte[] orderId = order().insert();
		insertHistory(orderId, null, "pending", null);

		assertThatThrownBy(() -> jdbc.update("DELETE FROM orders WHERE id = ?", (Object) orderId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("fk_order_status_history_order");
	}

	// --- outbox ---

	/** Servisler aynı ortak {@code OutboxEvent} entity'sini eşler; tablo tanımı hepsinde aynı olmalı. */
	@Test
	void outboxDdlIsIdenticalToUsers() throws IOException {
		String user = outboxDdl(Files.readString(USER_V1, StandardCharsets.UTF_8));
		String order = outboxDdl(
				new ClassPathResource("db/migration/V1__init_order.sql").getContentAsString(StandardCharsets.UTF_8));

		assertThat(order).isEqualTo(user);
	}

	@Test
	void outboxColumnsMatchSharedOutbox() {
		assertThat(columns("outbox")).containsExactly(
				tuple("id", "binary(16)", "NO", null, ""),
				tuple("aggregate_type", "varchar(64)", "NO", null, ""),
				tuple("aggregate_id", "binary(16)", "NO", null, ""),
				tuple("event_type", "varchar(64)", "NO", null, ""),
				tuple("payload", "json", "NO", null, ""),
				tuple("created_at", "datetime(6)", "NO", "CURRENT_TIMESTAMP(6)", "DEFAULT_GENERATED"),
				tuple("published_at", "datetime(6)", "YES", null, ""));
	}

	/** {@code CREATE TABLE outbox (...);} bloğu; yorumlar atılır, boşluklar tek boşluğa indirilir. */
	private static String outboxDdl(String sql) {
		String withoutComments = sql.replaceAll("--[^\\n]*", "");
		Matcher matcher = Pattern.compile("CREATE TABLE outbox \\(.*?\\) ENGINE[^;]*;", Pattern.DOTALL)
			.matcher(withoutComments);
		assertThat(matcher.find()).as("CREATE TABLE outbox bloğu").isTrue();
		return matcher.group().replaceAll("\\s+", " ").trim();
	}

	/**
	 * MySQL CHECK ihlalini SQLSTATE HY000 / hata 3819 ile bildirir; Spring bu durumu sınıflandıramaz ve
	 * JdbcTemplate {@link UncategorizedSQLException} fırlatır (DataIntegrityViolationException değil).
	 * Birden çok ad verilirse satır hepsini ihlal eder ve MySQL'in bildirdiği ilk kısıt bunlardan biri olmalı.
	 */
	private static void assertCheckViolation(ThrowingCallable statement, String... anyOfConstraints) {
		String[] messages = Arrays.stream(anyOfConstraints)
			.map(constraint -> "Check constraint '" + constraint + "' is violated")
			.toArray(String[]::new);
		assertThatThrownBy(statement)
			.isInstanceOfSatisfying(UncategorizedSQLException.class,
					ex -> assertThat(ex.getSQLException().getErrorCode()).isEqualTo(MYSQL_CHECK_CONSTRAINT_VIOLATED))
			.satisfies(ex -> assertThat(ex.getMessage()).containsAnyOf(messages));
	}

	private static void assertDuplicate(ThrowingCallable statement, String constraint) {
		assertThatThrownBy(statement).isInstanceOf(DuplicateKeyException.class).hasMessageContaining(constraint);
	}

	private List<Tuple> columns(String table) {
		return jdbc.query("""
				SELECT column_name, column_type, is_nullable, column_default, extra FROM information_schema.columns
				WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position
				""", (rs, i) -> tuple(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)),
				table);
	}

	/** Durum/stok birleşimini diğer kuralları (ödeme, hata kodu) karşılayarak kurar. */
	private OrderRow settledOrder(String status, String stockState) {
		OrderRow row = order().status(status).stock(stockState);
		return switch (status) {
			case "paid" -> row.payment(newId());
			case "failed" -> row.failure("ORDER_EXPIRED");
			default -> row;
		};
	}

	private OrderRow order() {
		return new OrderRow();
	}

	private void insertItem(byte[] orderId, byte[] bookId, int quantity, String unitPrice, String lineTotal) {
		insertItem(orderId, bookId, "Kitap", quantity, unitPrice, lineTotal);
	}

	private void insertItem(byte[] orderId, byte[] bookId, String title, int quantity, String unitPrice,
			String lineTotal) {
		jdbc.update("""
				INSERT INTO order_items (id, order_id, book_id, title_snapshot, quantity, unit_price, line_total)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", newId(), orderId, bookId, title, quantity, new BigDecimal(unitPrice), new BigDecimal(lineTotal));
	}

	private void insertHistory(byte[] orderId, String fromStatus, String toStatus, String reason) {
		jdbc.update("""
				INSERT INTO order_status_history (id, order_id, from_status, to_status, reason, created_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""", newId(), orderId, fromStatus, toStatus, reason, NOW);
	}

	private static String lineTotal(String unitPrice, int quantity) {
		return new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity)).toPlainString();
	}

	private static byte[] newId() {
		UUID uuid = UUID.randomUUID();
		return ByteBuffer.allocate(16)
			.putLong(uuid.getMostSignificantBits())
			.putLong(uuid.getLeastSignificantBits())
			.array();
	}

	/** Geçerli bir bekleyen sipariş satırı; testler yalnızca ilgilendikleri kolonu değiştirir. */
	private final class OrderRow {

		private final byte[] id = newId();

		private byte[] userId = newId();

		private String status = "pending";

		private String stockState = "requested";

		private String currency = "TRY";

		private String subtotal = "100.00";

		private String discount = "0.00";

		private String total = "100.00";

		private String address = ADDRESS;

		private byte[] paymentId;

		private String failureCode;

		private String latePaymentAt;

		OrderRow user(byte[] value) {
			this.userId = value;
			return this;
		}

		OrderRow status(String value) {
			this.status = value;
			return this;
		}

		OrderRow stock(String value) {
			this.stockState = value;
			return this;
		}

		OrderRow currency(String value) {
			this.currency = value;
			return this;
		}

		OrderRow amounts(String subtotalValue, String discountValue, String totalValue) {
			this.subtotal = subtotalValue;
			this.discount = discountValue;
			this.total = totalValue;
			return this;
		}

		OrderRow address(String value) {
			this.address = value;
			return this;
		}

		OrderRow payment(byte[] value) {
			this.paymentId = value;
			return this;
		}

		OrderRow failure(String value) {
			this.failureCode = value;
			return this;
		}

		OrderRow latePayment(String value) {
			this.latePaymentAt = value;
			return this;
		}

		byte[] insert() {
			jdbc.update("""
					INSERT INTO orders (id, user_id, cart_id, status, stock_state, currency, subtotal, discount_amount,
						total_amount, address_snapshot, payment_id, failure_code, late_payment_at, created_at,
						updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", this.id, this.userId, newId(), this.status, this.stockState, this.currency,
					new BigDecimal(this.subtotal), new BigDecimal(this.discount), new BigDecimal(this.total),
					this.address, this.paymentId, this.failureCode, this.latePaymentAt, NOW, NOW);
			return this.id;
		}

	}

}
