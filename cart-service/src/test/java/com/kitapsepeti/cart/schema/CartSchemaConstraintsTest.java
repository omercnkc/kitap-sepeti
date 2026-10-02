package com.kitapsepeti.cart.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kitapsepeti.cart.TestcontainersConfiguration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V1 şemasının yapısını ve kısıtlarının DB seviyesinde uygulandığını doğrular (entity yok, düz SQL).
 * Her test kendi transaction'ında koşar ve geri alınır.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CartSchemaConstraintsTest {

	private static final int MYSQL_CHECK_CONSTRAINT_VIOLATED = 3819;

	private static final String OLD_TIMESTAMP = "2020-01-01 00:00:00.000000";

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void flywayAppliesV1AndCreatesOnlyCartTables() {
		Integer applied = jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class);
		List<Map<String, Object>> tables = jdbc.queryForList("""
				SELECT table_name AS name, engine AS eng, table_collation AS collation FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
				""");

		assertThat(applied).isEqualTo(1);
		// Takma adlar küçük harf: satır map'i anahtarı JVM locale'iyle küçültür (tr-TR'de "ENGINE" → "engıne").
		assertThat(tables).extracting(t -> t.get("name"), t -> t.get("eng"), t -> t.get("collation"))
			.containsExactlyInAnyOrder(tuple("carts", "InnoDB", "utf8mb4_0900_ai_ci"),
					tuple("cart_items", "InnoDB", "utf8mb4_0900_ai_ci"));
	}

	@Test
	void cartsColumnsMatchV1() {
		assertThat(columns("carts")).containsExactly(
				tuple("id", "binary(16)", "NO", null, ""),
				tuple("user_id", "binary(16)", "NO", null, ""),
				tuple("status", "varchar(16)", "NO", "active", ""),
				tuple("active_user_id", "binary(16)", "YES", null, "VIRTUAL GENERATED"),
				tuple("created_at", "datetime(6)", "NO", "CURRENT_TIMESTAMP(6)", "DEFAULT_GENERATED"),
				tuple("updated_at", "datetime(6)", "NO", "CURRENT_TIMESTAMP(6)",
						"DEFAULT_GENERATED on update CURRENT_TIMESTAMP(6)"));
		String expression = jdbc.queryForObject("""
				SELECT generation_expression FROM information_schema.columns
				WHERE table_schema = DATABASE() AND table_name = 'carts' AND column_name = 'active_user_id'
				""", String.class);
		assertThat(expression).isEqualTo("(case when (`status` = _utf8mb4\\'active\\') then `user_id` end)");
	}

	@Test
	void onlyCartStatusUsesBinaryCollation() {
		List<Map<String, Object>> collations = jdbc.queryForList("""
				SELECT table_name AS t, column_name AS c, collation_name AS coll FROM information_schema.columns
				WHERE table_schema = DATABASE() AND table_name IN ('carts', 'cart_items') AND collation_name IS NOT NULL
				""");

		assertThat(collations).extracting(c -> c.get("t"), c -> c.get("c"), c -> c.get("coll"))
			.containsExactlyInAnyOrder(
					tuple("carts", "status", "utf8mb4_bin"),
					tuple("cart_items", "currency_snapshot", "utf8mb4_0900_ai_ci"),
					tuple("cart_items", "title_snapshot", "utf8mb4_0900_ai_ci"),
					tuple("cart_items", "cover_url_snapshot", "utf8mb4_0900_ai_ci"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "ACTIVE", "Active" })
	void rejectsStatusInOtherLetterCase(String status) {
		assertCheckViolation(() -> insertCart(newId(), status), "ck_carts_status");
	}

	@Test
	void cartItemsColumnsMatchV1() {
		assertThat(columns("cart_items")).containsExactly(
				tuple("id", "binary(16)", "NO", null, ""),
				tuple("cart_id", "binary(16)", "NO", null, ""),
				tuple("book_id", "binary(16)", "NO", null, ""),
				tuple("quantity", "int", "NO", null, ""),
				tuple("unit_price_snapshot", "decimal(12,2)", "NO", null, ""),
				tuple("currency_snapshot", "char(3)", "NO", "TRY", ""),
				tuple("title_snapshot", "varchar(300)", "NO", null, ""),
				tuple("cover_url_snapshot", "varchar(500)", "YES", null, ""),
				tuple("added_at", "datetime(6)", "NO", "CURRENT_TIMESTAMP(6)", "DEFAULT_GENERATED"),
				tuple("updated_at", "datetime(6)", "NO", "CURRENT_TIMESTAMP(6)",
						"DEFAULT_GENERATED on update CURRENT_TIMESTAMP(6)"));
	}

	@Test
	void constraintsAndIndexesHaveExplicitNames() {
		List<Map<String, Object>> constraints = jdbc.queryForList("""
				SELECT table_name AS t, constraint_name AS n, constraint_type AS k FROM information_schema.table_constraints
				WHERE table_schema = DATABASE() AND table_name IN ('carts', 'cart_items')
				""");
		// MySQL birincil anahtarı her zaman PRIMARY adıyla saklar (V1'deki pk_* adı yok sayılır; catalog'da da aynı).
		assertThat(constraints).extracting(c -> c.get("t"), c -> c.get("n"), c -> c.get("k"))
			.containsExactlyInAnyOrder(
					tuple("carts", "PRIMARY", "PRIMARY KEY"),
					tuple("carts", "uk_carts_active_user", "UNIQUE"),
					tuple("carts", "ck_carts_status", "CHECK"),
					tuple("cart_items", "PRIMARY", "PRIMARY KEY"),
					tuple("cart_items", "uk_cart_items_cart_book", "UNIQUE"),
					tuple("cart_items", "fk_cart_items_cart", "FOREIGN KEY"),
					tuple("cart_items", "ck_cart_items_quantity", "CHECK"),
					tuple("cart_items", "ck_cart_items_price", "CHECK"));

		List<Map<String, Object>> checks = jdbc.queryForList("""
				SELECT constraint_name AS n, check_clause AS c FROM information_schema.check_constraints
				WHERE constraint_schema = DATABASE()
				""");
		assertThat(checks).extracting(c -> c.get("n"), c -> c.get("c"))
			.containsExactlyInAnyOrder(
					tuple("ck_carts_status", "(`status` in (_utf8mb4\\'active\\',_utf8mb4\\'checked_out\\',_utf8mb4\\'abandoned\\'))"),
					tuple("ck_cart_items_quantity", "(`quantity` between 1 and 99)"),
					tuple("ck_cart_items_price", "(`unit_price_snapshot` >= 0)"));

		Map<String, Object> fk = jdbc.queryForMap("""
				SELECT referenced_table_name AS ref, delete_rule AS del FROM information_schema.referential_constraints
				WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_cart_items_cart'
				""");
		assertThat(fk).containsEntry("ref", "carts").containsEntry("del", "CASCADE");

		List<Map<String, Object>> indexes = jdbc.queryForList("""
				SELECT table_name AS t, index_name AS i, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
				FROM information_schema.statistics
				WHERE table_schema = DATABASE() AND table_name IN ('carts', 'cart_items')
				GROUP BY table_name, index_name
				""");
		assertThat(indexes).extracting(i -> i.get("t"), i -> i.get("i"), i -> i.get("cols"))
			.containsExactlyInAnyOrder(
					tuple("carts", "PRIMARY", "id"),
					tuple("carts", "uk_carts_active_user", "active_user_id"),
					tuple("carts", "ix_carts_user", "user_id"),
					tuple("cart_items", "PRIMARY", "id"),
					tuple("cart_items", "uk_cart_items_cart_book", "cart_id,book_id"));
	}

	@Test
	void rejectsSecondActiveCartForSameUser() {
		byte[] userId = newId();
		insertCart(userId, "active");

		assertDuplicate(() -> insertCart(userId, "active"), "uk_carts_active_user");
	}

	@Test
	void allowsHistoricCartsAlongsideOneActiveCart() {
		byte[] userId = newId();
		insertCart(userId, "active");
		insertCart(userId, "checked_out");
		insertCart(userId, "checked_out");
		insertCart(userId, "abandoned");
		insertCart(userId, "abandoned");

		assertThat(count("carts", "user_id", userId)).isEqualTo(5);
		assertThat(count("carts", "active_user_id", userId)).isEqualTo(1);
	}

	@Test
	void historicCartsWithoutActiveCartAreAllowed() {
		byte[] userId = newId();
		insertCart(userId, "checked_out");
		insertCart(userId, "checked_out");
		insertCart(userId, "abandoned");
		insertCart(userId, "abandoned");

		assertThat(count("carts", "user_id", userId)).isEqualTo(4);
		assertThat(count("carts", "active_user_id", userId)).isZero();
	}

	@Test
	void checkingOutActiveCartAllowsNewActiveCart() {
		byte[] userId = newId();
		byte[] first = insertCart(userId, "active");

		jdbc.update("UPDATE carts SET status = 'checked_out' WHERE id = ?", (Object) first);
		byte[] second = insertCart(userId, "active");

		assertThat(jdbc.queryForObject("SELECT id FROM carts WHERE active_user_id = ?", byte[].class, (Object) userId))
			.isEqualTo(second);
	}

	@Test
	void reactivatingHistoricCartWhileAnotherIsActiveIsRejected() {
		byte[] userId = newId();
		byte[] old = insertCart(userId, "abandoned");
		insertCart(userId, "active");

		assertDuplicate(() -> jdbc.update("UPDATE carts SET status = 'active' WHERE id = ?", (Object) old),
				"uk_carts_active_user");
	}

	@Test
	void rejectsUnknownCartStatus() {
		assertCheckViolation(() -> insertCart(newId(), "deleted"), "ck_carts_status");

		byte[] cartId = insertCart(newId(), "active");
		assertCheckViolation(() -> jdbc.update("UPDATE carts SET status = 'expired' WHERE id = ?", (Object) cartId),
				"ck_carts_status");
	}

	@Test
	void statusDefaultsToActive() {
		byte[] id = newId();
		jdbc.update("INSERT INTO carts (id, user_id) VALUES (?, ?)", id, newId());

		assertThat(jdbc.queryForObject("SELECT status FROM carts WHERE id = ?", String.class, (Object) id))
			.isEqualTo("active");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 100, -1 })
	void rejectsQuantityOutsideOneToNinetyNine(int quantity) {
		byte[] cartId = insertCart(newId(), "active");

		assertCheckViolation(() -> insertItem(cartId, newId(), quantity, new BigDecimal("10.00")),
				"ck_cart_items_quantity");
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 99 })
	void acceptsQuantityBounds(int quantity) {
		byte[] cartId = insertCart(newId(), "active");
		byte[] itemId = insertItem(cartId, newId(), quantity, new BigDecimal("10.00"));

		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id = ?", Integer.class, (Object) itemId))
			.isEqualTo(quantity);
	}

	@Test
	void rejectsNegativePriceButAllowsZero() {
		byte[] cartId = insertCart(newId(), "active");

		assertCheckViolation(() -> insertItem(cartId, newId(), 1, new BigDecimal("-0.01")), "ck_cart_items_price");
		insertItem(cartId, newId(), 1, BigDecimal.ZERO);
	}

	@Test
	void itemDefaultsToTryCurrency() {
		byte[] itemId = insertItem(insertCart(newId(), "active"), newId(), 1, new BigDecimal("10.00"));

		assertThat(jdbc.queryForObject("SELECT currency_snapshot FROM cart_items WHERE id = ?", String.class,
				(Object) itemId))
			.isEqualTo("TRY");
	}

	@Test
	void rejectsSameBookTwiceInOneCartButAllowsItInAnotherCart() {
		byte[] bookId = newId();
		byte[] cartId = insertCart(newId(), "active");
		insertItem(cartId, bookId, 1, new BigDecimal("10.00"));

		assertDuplicate(() -> insertItem(cartId, bookId, 2, new BigDecimal("10.00")), "uk_cart_items_cart_book");
		insertItem(insertCart(newId(), "active"), bookId, 1, new BigDecimal("10.00"));
		assertThat(count("cart_items", "book_id", bookId)).isEqualTo(2);
	}

	@Test
	void deletingCartCascadesToItems() {
		byte[] cartId = insertCart(newId(), "active");
		byte[] otherCartId = insertCart(newId(), "active");
		insertItem(cartId, newId(), 1, new BigDecimal("10.00"));
		insertItem(cartId, newId(), 2, new BigDecimal("20.00"));
		insertItem(otherCartId, newId(), 1, new BigDecimal("10.00"));

		jdbc.update("DELETE FROM carts WHERE id = ?", (Object) cartId);

		assertThat(count("cart_items", "cart_id", cartId)).isZero();
		assertThat(count("cart_items", "cart_id", otherCartId)).isEqualTo(1);
	}

	@Test
	void rejectsItemForMissingCart() {
		assertThatThrownBy(() -> insertItem(newId(), newId(), 1, new BigDecimal("10.00")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("fk_cart_items_cart");
	}

	/**
	 * Eski değer SQL metniyle yazılır ve karşılaştırma DB içinde yapılır: JDBC'ye java.sql.Timestamp verilseydi
	 * Connector/J onu JVM saat diliminde yazardı (techContext'teki tuzak).
	 */
	@Test
	void updatedAtIsRefreshedByDatabaseOnUpdate() {
		byte[] cartId = insertCart(newId(), "active");
		byte[] itemId = insertItem(cartId, newId(), 1, new BigDecimal("10.00"));
		jdbc.update("UPDATE carts SET created_at = ?, updated_at = ? WHERE id = ?", OLD_TIMESTAMP, OLD_TIMESTAMP,
				cartId);
		jdbc.update("UPDATE cart_items SET added_at = ?, updated_at = ? WHERE id = ?", OLD_TIMESTAMP, OLD_TIMESTAMP,
				itemId);

		jdbc.update("UPDATE carts SET status = 'checked_out' WHERE id = ?", (Object) cartId);
		jdbc.update("UPDATE cart_items SET quantity = 2 WHERE id = ?", (Object) itemId);

		assertRefreshed("carts", "created_at", cartId);
		assertRefreshed("cart_items", "added_at", itemId);
	}

	private void assertRefreshed(String table, String createdColumn, byte[] id) {
		Map<String, Object> row = jdbc.queryForMap("SELECT " + createdColumn + " = ? AS created_kept, "
				+ "ABS(TIMESTAMPDIFF(SECOND, updated_at, CURRENT_TIMESTAMP(6))) AS age_seconds FROM " + table
				+ " WHERE id = ?", OLD_TIMESTAMP, id);
		assertThat(((Number) row.get("created_kept")).intValue()).as(table + "." + createdColumn + " değişmedi")
			.isEqualTo(1);
		assertThat(((Number) row.get("age_seconds")).longValue()).as(table + ".updated_at şimdiye güncellendi")
			.isLessThan(60);
	}

	/**
	 * MySQL CHECK ihlalini SQLSTATE HY000 / hata 3819 ile bildirir; Spring bu durumu sınıflandıramaz ve
	 * JdbcTemplate {@link UncategorizedSQLException} fırlatır (DataIntegrityViolationException değil).
	 */
	private static void assertCheckViolation(ThrowingCallable statement, String constraint) {
		assertThatThrownBy(statement)
			.isInstanceOfSatisfying(UncategorizedSQLException.class,
					ex -> assertThat(ex.getSQLException().getErrorCode()).isEqualTo(MYSQL_CHECK_CONSTRAINT_VIOLATED))
			.hasMessageContaining("Check constraint '" + constraint + "' is violated");
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

	private byte[] insertCart(byte[] userId, String status) {
		byte[] id = newId();
		jdbc.update("INSERT INTO carts (id, user_id, status) VALUES (?, ?, ?)", id, userId, status);
		return id;
	}

	private byte[] insertItem(byte[] cartId, byte[] bookId, int quantity, BigDecimal unitPrice) {
		byte[] id = newId();
		jdbc.update("""
				INSERT INTO cart_items (id, cart_id, book_id, quantity, unit_price_snapshot, title_snapshot)
				VALUES (?, ?, ?, ?, ?, ?)
				""", id, cartId, bookId, quantity, unitPrice, "Kitap");
		return id;
	}

	private int count(String table, String column, byte[] value) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class,
				(Object) value);
	}

	private static byte[] newId() {
		UUID uuid = UUID.randomUUID();
		return ByteBuffer.allocate(16)
			.putLong(uuid.getMostSignificantBits())
			.putLong(uuid.getLeastSignificantBits())
			.array();
	}

}
