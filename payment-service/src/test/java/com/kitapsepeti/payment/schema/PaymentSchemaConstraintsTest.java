package com.kitapsepeti.payment.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kitapsepeti.payment.TestcontainersConfiguration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
 * V1 şemasının (V2 collation değişikliğiyle) yapısını ve kısıtlarının DB seviyesinde uygulandığını doğrular
 * (entity yok, düz SQL).
 * Her test kendi transaction'ında koşar ve geri alınır.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PaymentSchemaConstraintsTest {

	private static final int MYSQL_CHECK_CONSTRAINT_VIOLATED = 3819;

	private static final String NOW = "2026-01-01 10:00:00.000000";

	private static final Path CATALOG_V1 = Path.of("..", "catalog-service", "src", "main", "resources", "db",
			"migration", "V1__create_catalog_tables.sql");

	private static final Path USER_V1 = Path.of("..", "user-service", "src", "main", "resources", "db", "migration",
			"V1__create_user_tables.sql");

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void flywayAppliesV1AndV2AndCreatesOnlyPaymentTables() {
		List<String> applied = jdbc.queryForList(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);
		List<Map<String, Object>> tables = jdbc.queryForList("""
				SELECT table_name AS name, engine AS eng, table_collation AS collation FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
				""");

		assertThat(applied).containsExactly("1", "2");
		// Takma adlar küçük harf: satır map'i anahtarı JVM locale'iyle küçültür (tr-TR'de "ENGINE" → "engıne").
		assertThat(tables).extracting(t -> t.get("name"), t -> t.get("eng"), t -> t.get("collation"))
			.containsExactlyInAnyOrder(tuple("payments", "InnoDB", "utf8mb4_0900_ai_ci"),
					tuple("provider_events", "InnoDB", "utf8mb4_0900_ai_ci"),
					tuple("outbox", "InnoDB", "utf8mb4_0900_ai_ci"));
	}

	@Test
	void paymentsColumnsMatchV1() {
		assertThat(columns("payments")).containsExactly(
				tuple("id", "binary(16)", "NO", null, ""),
				tuple("order_id", "binary(16)", "NO", null, ""),
				tuple("user_id", "binary(16)", "NO", null, ""),
				tuple("provider", "varchar(16)", "NO", null, ""),
				tuple("provider_payment_id", "varchar(128)", "YES", null, ""),
				tuple("amount", "decimal(12,2)", "NO", null, ""),
				tuple("currency", "char(3)", "NO", "TRY", ""),
				tuple("status", "varchar(16)", "NO", "initiated", ""),
				tuple("failure_code", "varchar(64)", "YES", null, ""),
				tuple("created_at", "datetime(6)", "NO", null, ""),
				tuple("updated_at", "datetime(6)", "NO", null, ""));
	}

	@Test
	void providerEventsColumnsMatchV1() {
		assertThat(columns("provider_events")).containsExactly(
				tuple("id", "binary(16)", "NO", null, ""),
				tuple("provider", "varchar(16)", "NO", null, ""),
				tuple("provider_event_id", "varchar(128)", "NO", null, ""),
				tuple("payment_id", "binary(16)", "NO", null, ""),
				tuple("event_type", "varchar(32)", "NO", null, ""),
				tuple("processed_at", "datetime(6)", "NO", null, ""));
	}

	/** Enum benzeri kolonlar V1'den, sağlayıcı kimlikleri V2'den beri {@code utf8mb4_bin}. */
	@Test
	void enumLikeAndProviderIdColumnsUseBinaryCollation() {
		List<Map<String, Object>> collations = jdbc.queryForList("""
				SELECT table_name AS t, column_name AS c, collation_name AS coll FROM information_schema.columns
				WHERE table_schema = DATABASE() AND table_name IN ('payments', 'provider_events', 'outbox')
				AND collation_name IS NOT NULL
				""");

		assertThat(collations).extracting(c -> c.get("t"), c -> c.get("c"), c -> c.get("coll"))
			.containsExactlyInAnyOrder(
					tuple("payments", "provider", "utf8mb4_bin"),
					tuple("payments", "provider_payment_id", "utf8mb4_bin"),
					tuple("payments", "currency", "utf8mb4_bin"),
					tuple("payments", "status", "utf8mb4_bin"),
					tuple("payments", "failure_code", "utf8mb4_0900_ai_ci"),
					tuple("provider_events", "provider", "utf8mb4_bin"),
					tuple("provider_events", "provider_event_id", "utf8mb4_bin"),
					tuple("provider_events", "event_type", "utf8mb4_bin"),
					tuple("outbox", "aggregate_type", "utf8mb4_0900_ai_ci"),
					tuple("outbox", "event_type", "utf8mb4_0900_ai_ci"));
	}

	@Test
	void constraintsAndIndexesHaveExplicitNames() {
		List<Map<String, Object>> constraints = jdbc.queryForList("""
				SELECT table_name AS t, constraint_name AS n, constraint_type AS k FROM information_schema.table_constraints
				WHERE table_schema = DATABASE() AND table_name IN ('payments', 'provider_events', 'outbox')
				""");
		// MySQL birincil anahtarı her zaman PRIMARY adıyla saklar (V1'deki pk_* adı yok sayılır).
		assertThat(constraints).extracting(c -> c.get("t"), c -> c.get("n"), c -> c.get("k"))
			.containsExactlyInAnyOrder(
					tuple("payments", "PRIMARY", "PRIMARY KEY"),
					tuple("payments", "uk_payments_order", "UNIQUE"),
					tuple("payments", "uk_payments_provider_ref", "UNIQUE"),
					tuple("payments", "ck_payments_provider", "CHECK"),
					tuple("payments", "ck_payments_amount", "CHECK"),
					tuple("payments", "ck_payments_currency", "CHECK"),
					tuple("payments", "ck_payments_status", "CHECK"),
					tuple("payments", "ck_payments_failure", "CHECK"),
					tuple("provider_events", "PRIMARY", "PRIMARY KEY"),
					tuple("provider_events", "uk_provider_events_provider_event", "UNIQUE"),
					tuple("provider_events", "fk_provider_events_payment", "FOREIGN KEY"),
					tuple("provider_events", "ck_provider_events_provider", "CHECK"),
					tuple("provider_events", "ck_provider_events_event_type", "CHECK"),
					tuple("outbox", "PRIMARY", "PRIMARY KEY"));

		List<Map<String, Object>> checks = jdbc.queryForList("""
				SELECT constraint_name AS n, check_clause AS c FROM information_schema.check_constraints
				WHERE constraint_schema = DATABASE()
				""");
		String providers = "(_utf8mb4\\'mock\\',_utf8mb4\\'iyzico\\',_utf8mb4\\'paytr\\',_utf8mb4\\'stripe\\')";
		assertThat(checks).extracting(c -> c.get("n"), c -> c.get("c"))
			.containsExactlyInAnyOrder(
					tuple("ck_payments_provider", "(`provider` in " + providers + ")"),
					tuple("ck_payments_amount", "(`amount` > 0)"),
					tuple("ck_payments_currency", "regexp_like(`currency`,_utf8mb4\\'^[A-Z]{3}$\\',_utf8mb4\\'c\\')"),
					tuple("ck_payments_status",
							"(`status` in (_utf8mb4\\'initiated\\',_utf8mb4\\'succeeded\\',_utf8mb4\\'failed\\'))"),
					tuple("ck_payments_failure", "((`status` = _utf8mb4\\'failed\\') = (`failure_code` is not null))"),
					tuple("ck_provider_events_provider", "(`provider` in " + providers + ")"),
					tuple("ck_provider_events_event_type",
							"(`event_type` in (_utf8mb4\\'payment.succeeded\\',_utf8mb4\\'payment.failed\\'))"));

		Map<String, Object> fk = jdbc.queryForMap("""
				SELECT referenced_table_name AS ref, delete_rule AS del FROM information_schema.referential_constraints
				WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_provider_events_payment'
				""");
		assertThat(fk).containsEntry("ref", "payments").containsEntry("del", "RESTRICT");

		List<Map<String, Object>> indexes = jdbc.queryForList("""
				SELECT table_name AS t, index_name AS i, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
				FROM information_schema.statistics
				WHERE table_schema = DATABASE() AND table_name IN ('payments', 'provider_events', 'outbox')
				GROUP BY table_name, index_name
				""");
		// FK için ayrı (fk_* adlı) indeks oluşmaz; ix_provider_events_payment kullanılır.
		assertThat(indexes).extracting(i -> i.get("t"), i -> i.get("i"), i -> i.get("cols"))
			.containsExactlyInAnyOrder(
					tuple("payments", "PRIMARY", "id"),
					tuple("payments", "uk_payments_order", "order_id"),
					tuple("payments", "uk_payments_provider_ref", "provider,provider_payment_id"),
					tuple("payments", "ix_payments_status_created", "status,created_at"),
					tuple("provider_events", "PRIMARY", "id"),
					tuple("provider_events", "uk_provider_events_provider_event", "provider,provider_event_id"),
					tuple("provider_events", "ix_provider_events_payment", "payment_id"),
					tuple("outbox", "PRIMARY", "id"),
					tuple("outbox", "ix_outbox_published_at_created_at", "published_at,created_at"));
	}

	@Test
	void noCardDataColumnsInAnyTable() {
		List<String> suspicious = jdbc.queryForList("""
				SELECT CONCAT(table_name, '.', column_name) FROM information_schema.columns
				WHERE table_schema = DATABASE()
				AND (LOWER(column_name) LIKE '%card%' OR LOWER(column_name) LIKE '%pan%'
					OR LOWER(column_name) LIKE '%cvv%' OR LOWER(column_name) LIKE '%cvc%'
					OR LOWER(column_name) LIKE '%expiry%')
				""", String.class);

		assertThat(suspicious).isEmpty();
	}

	// --- payments ---

	@Test
	void rejectsSecondPaymentForSameOrder() {
		byte[] orderId = newId();
		insertPayment(orderId, "mock", null, "10.00", "TRY", "initiated", null);

		assertDuplicate(() -> insertPayment(orderId, "mock", null, "10.00", "TRY", "initiated", null),
				"uk_payments_order");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Initiated", "SUCCEEDED", "pending" })
	void rejectsUnknownOrMiscasedStatus(String status) {
		assertCheckViolation(() -> insertPayment(newId(), "mock", null, "10.00", "TRY", status, null),
				"ck_payments_status");
	}

	@Test
	void statusAndCurrencyHaveDefaults() {
		byte[] id = newId();
		jdbc.update("""
				INSERT INTO payments (id, order_id, user_id, provider, amount, created_at, updated_at)
				VALUES (?, ?, ?, 'mock', 10.00, ?, ?)
				""", id, newId(), newId(), NOW, NOW);

		Map<String, Object> row = jdbc.queryForMap("SELECT status AS s, currency AS c FROM payments WHERE id = ?",
				(Object) id);
		assertThat(row).containsEntry("s", "initiated").containsEntry("c", "TRY");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Mock", "paypal" })
	void rejectsUnknownOrMiscasedProvider(String provider) {
		assertCheckViolation(() -> insertPayment(newId(), provider, null, "10.00", "TRY", "initiated", null),
				"ck_payments_provider");
	}

	@ParameterizedTest
	@ValueSource(strings = { "mock", "iyzico", "paytr", "stripe" })
	void acceptsKnownProviders(String provider) {
		insertPayment(newId(), provider, null, "10.00", "TRY", "initiated", null);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.00", "-1" })
	void rejectsNonPositiveAmount(String amount) {
		assertCheckViolation(() -> insertPayment(newId(), "mock", null, amount, "TRY", "initiated", null),
				"ck_payments_amount");
	}

	@Test
	void acceptsSmallestPositiveAmount() {
		insertPayment(newId(), "mock", null, "0.01", "TRY", "initiated", null);
	}

	@ParameterizedTest
	@ValueSource(strings = { "try", "Try", "TR", "T1Y" })
	void rejectsCurrencyThatIsNotThreeUpperCaseLetters(String currency) {
		assertCheckViolation(() -> insertPayment(newId(), "mock", null, "10.00", currency, "initiated", null),
				"ck_payments_currency");
	}

	/** CHAR(3) sığmayan değeri CHECK'e gelmeden reddeder (strict mode, hata 1406 "Data too long"). */
	@Test
	void rejectsFourLetterCurrencyAsTooLong() {
		assertThatThrownBy(() -> insertPayment(newId(), "mock", null, "10.00", "TRYY", "initiated", null))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("Data too long for column 'currency'");
	}

	@ParameterizedTest
	@ValueSource(strings = { "TRY", "USD" })
	void acceptsUpperCaseCurrency(String currency) {
		insertPayment(newId(), "mock", null, "10.00", currency, "initiated", null);
	}

	@Test
	void failedPaymentRequiresFailureCode() {
		assertCheckViolation(() -> insertPayment(newId(), "mock", null, "10.00", "TRY", "failed", null),
				"ck_payments_failure");
	}

	@ParameterizedTest
	@ValueSource(strings = { "succeeded", "initiated" })
	void onlyFailedPaymentMayHaveFailureCode(String status) {
		assertCheckViolation(() -> insertPayment(newId(), "mock", null, "10.00", "TRY", status, "card_declined"),
				"ck_payments_failure");
	}

	@Test
	void failedPaymentWithCodeIsAccepted() {
		byte[] id = insertPayment(newId(), "mock", null, "10.00", "TRY", "failed", "card_declined");

		assertThat(jdbc.queryForObject("SELECT failure_code FROM payments WHERE id = ?", String.class, (Object) id))
			.isEqualTo("card_declined");
	}

	@Test
	void succeedingPaymentKeepsFailureCodeRuleOnUpdate() {
		byte[] id = insertPayment(newId(), "mock", null, "10.00", "TRY", "initiated", null);

		assertCheckViolation(() -> jdbc.update("UPDATE payments SET status = 'failed' WHERE id = ?", (Object) id),
				"ck_payments_failure");
		jdbc.update("UPDATE payments SET status = 'succeeded' WHERE id = ?", (Object) id);
	}

	@Test
	void rejectsDuplicateProviderReference() {
		insertPayment(newId(), "mock", "mock_ref_1", "10.00", "TRY", "initiated", null);

		assertDuplicate(() -> insertPayment(newId(), "mock", "mock_ref_1", "10.00", "TRY", "initiated", null),
				"uk_payments_provider_ref");
	}

	/** V2: harf büyüklüğü farklı iki sağlayıcı referansı farklı ödemelerdir. */
	@Test
	void providerReferencesDifferingOnlyInCaseDoNotCollide() {
		insertPayment(newId(), "stripe", "pi_3Abc", "10.00", "TRY", "initiated", null);
		insertPayment(newId(), "stripe", "pi_3abc", "10.00", "TRY", "initiated", null);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE provider_payment_id = 'pi_3abc'",
				Integer.class)).isEqualTo(1);
		assertDuplicate(() -> insertPayment(newId(), "stripe", "pi_3Abc", "10.00", "TRY", "initiated", null),
				"uk_payments_provider_ref");
	}

	@Test
	void sameReferenceUnderAnotherProviderIsAccepted() {
		insertPayment(newId(), "mock", "ref_1", "10.00", "TRY", "initiated", null);
		insertPayment(newId(), "stripe", "ref_1", "10.00", "TRY", "initiated", null);
	}

	@Test
	void manyPaymentsWithoutProviderReferenceAreAccepted() {
		insertPayment(newId(), "mock", null, "10.00", "TRY", "initiated", null);
		insertPayment(newId(), "mock", null, "10.00", "TRY", "initiated", null);

		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE provider = 'mock' AND provider_payment_id IS NULL", Integer.class))
			.isGreaterThanOrEqualTo(2);
	}

	// --- provider_events ---

	@Test
	void rejectsSameProviderEventTwice() {
		byte[] paymentId = insertPayment(newId(), "mock", null, "10.00", "TRY", "initiated", null);
		insertEvent("mock", "evt_1", paymentId, "payment.succeeded");

		assertDuplicate(() -> insertEvent("mock", "evt_1", paymentId, "payment.succeeded"),
				"uk_provider_events_provider_event");
		insertEvent("stripe", "evt_1", paymentId, "payment.succeeded");
	}

	/** V2: olay kimlikleri de harf büyüklüğüne duyarlı. */
	@Test
	void providerEventIdsDifferingOnlyInCaseDoNotCollide() {
		byte[] paymentId = insertPayment(newId(), "mock", null, "10.00", "TRY", "initiated", null);
		insertEvent("mock", "evt_Case", paymentId, "payment.succeeded");
		insertEvent("mock", "evt_case", paymentId, "payment.succeeded");

		assertDuplicate(() -> insertEvent("mock", "evt_Case", paymentId, "payment.failed"),
				"uk_provider_events_provider_event");
	}

	@Test
	void rejectsEventForMissingPayment() {
		assertThatThrownBy(() -> insertEvent("mock", "evt_missing", newId(), "payment.failed"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("fk_provider_events_payment");
	}

	@Test
	void paymentWithEventsCannotBeDeleted() {
		byte[] paymentId = insertPayment(newId(), "mock", null, "10.00", "TRY", "initiated", null);
		insertEvent("mock", "evt_2", paymentId, "payment.succeeded");

		assertThatThrownBy(() -> jdbc.update("DELETE FROM payments WHERE id = ?", (Object) paymentId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("fk_provider_events_payment");
	}

	@Test
	void paymentWithoutEventsCanBeDeleted() {
		byte[] paymentId = insertPayment(newId(), "mock", null, "10.00", "TRY", "initiated", null);

		assertThat(jdbc.update("DELETE FROM payments WHERE id = ?", (Object) paymentId)).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "PAYMENT.SUCCEEDED", "Payment.failed", "payment.refunded" })
	void rejectsUnknownOrMiscasedEventType(String eventType) {
		byte[] paymentId = insertPayment(newId(), "mock", null, "10.00", "TRY", "initiated", null);

		assertCheckViolation(() -> insertEvent("mock", "evt_" + eventType, paymentId, eventType),
				"ck_provider_events_event_type");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Mock", "paypal" })
	void rejectsUnknownOrMiscasedEventProvider(String provider) {
		byte[] paymentId = insertPayment(newId(), "mock", null, "10.00", "TRY", "initiated", null);

		assertCheckViolation(() -> insertEvent(provider, "evt_p", paymentId, "payment.succeeded"),
				"ck_provider_events_provider");
	}

	// --- outbox ---

	@Test
	void outboxDdlIsIdenticalToCatalogs() throws IOException {
		String catalog = outboxDdl(Files.readString(CATALOG_V1, StandardCharsets.UTF_8));
		String payment = outboxDdl(new ClassPathResource("db/migration/V1__create_payment_tables.sql")
			.getContentAsString(StandardCharsets.UTF_8));

		assertThat(payment).isEqualTo(catalog);
	}

	/** Üç servis aynı ortak {@code OutboxEvent} entity'sini eşler; tablo tanımı hepsinde aynı olmalı. */
	@Test
	void outboxDdlIsIdenticalToUsers() throws IOException {
		String user = outboxDdl(Files.readString(USER_V1, StandardCharsets.UTF_8));
		String payment = outboxDdl(new ClassPathResource("db/migration/V1__create_payment_tables.sql")
			.getContentAsString(StandardCharsets.UTF_8));

		assertThat(payment).isEqualTo(user);
	}

	@Test
	void outboxColumnsMatchCatalogOutbox() {
		assertThat(columns("outbox")).containsExactly(
				tuple("id", "binary(16)", "NO", null, ""),
				tuple("aggregate_type", "varchar(64)", "NO", null, ""),
				tuple("aggregate_id", "binary(16)", "NO", null, ""),
				tuple("event_type", "varchar(64)", "NO", null, ""),
				tuple("payload", "json", "NO", null, ""),
				tuple("created_at", "datetime(6)", "NO", "CURRENT_TIMESTAMP(6)", "DEFAULT_GENERATED"),
				tuple("published_at", "datetime(6)", "YES", null, ""));
	}

	@Test
	void outboxRowStartsUnpublishedWithCreatedAt() {
		byte[] id = newId();
		jdbc.update("""
				INSERT INTO outbox (id, aggregate_type, aggregate_id, event_type, payload)
				VALUES (?, 'Payment', ?, 'PaymentSucceeded', '{"eventVersion":1}')
				""", id, newId());

		Map<String, Object> row = jdbc.queryForMap("""
				SELECT published_at IS NULL AS unpublished, created_at IS NOT NULL AS stamped FROM outbox WHERE id = ?
				""", (Object) id);
		assertThat(((Number) row.get("unpublished")).intValue()).isEqualTo(1);
		assertThat(((Number) row.get("stamped")).intValue()).isEqualTo(1);
	}

	@Test
	void outboxRejectsInvalidJsonPayload() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO outbox (id, aggregate_type, aggregate_id, event_type, payload)
				VALUES (?, 'Payment', ?, 'PaymentSucceeded', 'not-json')
				""", newId(), newId()))
			.hasMessageContaining("Invalid JSON text");
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

	private byte[] insertPayment(byte[] orderId, String provider, String providerPaymentId, String amount,
			String currency, String status, String failureCode) {
		byte[] id = newId();
		jdbc.update("""
				INSERT INTO payments (id, order_id, user_id, provider, provider_payment_id, amount, currency, status,
					failure_code, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", id, orderId, newId(), provider, providerPaymentId, new BigDecimal(amount), currency, status,
				failureCode, NOW, NOW);
		return id;
	}

	private byte[] insertEvent(String provider, String providerEventId, byte[] paymentId, String eventType) {
		byte[] id = newId();
		jdbc.update("""
				INSERT INTO provider_events (id, provider, provider_event_id, payment_id, event_type, processed_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""", id, provider, providerEventId, paymentId, eventType, NOW);
		return id;
	}

	private static byte[] newId() {
		UUID uuid = UUID.randomUUID();
		return ByteBuffer.allocate(16)
			.putLong(uuid.getMostSignificantBits())
			.putLong(uuid.getLeastSignificantBits())
			.array();
	}

}
