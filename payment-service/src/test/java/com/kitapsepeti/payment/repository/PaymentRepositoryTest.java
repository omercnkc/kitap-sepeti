package com.kitapsepeti.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import com.kitapsepeti.common.error.DbConstraints;
import com.kitapsepeti.payment.TestcontainersConfiguration;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.entity.PaymentStatus;
import com.kitapsepeti.payment.entity.ProviderEvent;
import com.kitapsepeti.payment.entity.ProviderEventType;
import com.kitapsepeti.payment.entity.TransitionResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Entity eşlemeleri ve repository sorguları (gerçek MySQL, Flyway V1, ddl-auto validate).
 * Her test kendi transaction'ında koşar ve geri alınır; DB'deki değer aynı transaction'da JDBC ile okunur.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PaymentRepositoryTest {

	private static final Clock T1 = Clock.fixed(Instant.parse("2026-03-01T10:15:30.123456789Z"), ZoneOffset.UTC);

	private static final Clock T2 = Clock.fixed(Instant.parse("2026-03-01T10:16:00.000001Z"), ZoneOffset.UTC);

	@Autowired
	private PaymentRepository payments;

	@Autowired
	private ProviderEventRepository events;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	private final UUID orderId = UUID.randomUUID();

	private final UUID userId = UUID.randomUUID();

	@Test
	void contextStartsWithSchemaValidation() {
		Object ddlAuto = entityManager.getEntityManagerFactory().getProperties().get("hibernate.hbm2ddl.auto");

		assertThat(ddlAuto).isEqualTo("validate");
		assertThat(entityManager.getMetamodel().getEntities()).extracting(EntityType::getName)
			.containsExactlyInAnyOrder("Payment", "ProviderEvent");
	}

	@Test
	void paymentIsStoredWithUuidV7LowerCaseEnumsScaleTwoAndUtcMicros() {
		Payment payment = payments.saveAndFlush(initiate(new BigDecimal("149.9")));

		assertThat(payment.getId().version()).isEqualTo(7);
		Map<String, Object> row = jdbc.queryForMap("""
				SELECT BIN_TO_UUID(order_id) AS o, BIN_TO_UUID(user_id) AS u, provider AS p, status AS s,
					CAST(amount AS CHAR) AS a, currency AS c, provider_payment_id AS ref, failure_code AS f,
					DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created,
					DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') AS updated
				FROM payments WHERE id = ?
				""", (Object) bytes(payment.getId()));
		assertThat(row).containsEntry("o", orderId.toString())
			.containsEntry("u", userId.toString())
			.containsEntry("p", "mock")
			.containsEntry("s", "initiated")
			.containsEntry("a", "149.90")
			.containsEntry("c", "TRY")
			.containsEntry("ref", null)
			.containsEntry("f", null)
			// Saat 10:15 UTC verildi; DB'de de 10:15 (JVM saat dilimi UTC+3 olsa bile), mikrosaniyeye kadar.
			.containsEntry("created", "2026-03-01 10:15:30.123456")
			.containsEntry("updated", "2026-03-01 10:15:30.123456");
	}

	@Test
	void reloadedPaymentEqualsSavedValues() {
		Payment saved = initiate(new BigDecimal("10.5"));
		saved.attachProviderReference("mock_ref", T2);
		payments.saveAndFlush(saved);
		entityManager.clear();

		Payment loaded = payments.findById(saved.getId()).orElseThrow();

		assertThat(loaded).isNotSameAs(saved);
		assertThat(loaded.getOrderId()).isEqualTo(orderId);
		assertThat(loaded.getUserId()).isEqualTo(userId);
		assertThat(loaded.getAmount()).isEqualTo(new BigDecimal("10.50"));
		assertThat(loaded.getProviderType()).isEqualTo(PaymentProviderType.MOCK);
		assertThat(loaded.getProviderPaymentId()).isEqualTo("mock_ref");
		assertThat(loaded.getStatus()).isEqualTo(PaymentStatus.INITIATED);
		assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
		assertThat(loaded.getUpdatedAt()).isEqualTo(Instant.parse("2026-03-01T10:16:00.000001Z"));
	}

	@Test
	void findsByOrderIdAndByProviderReference() {
		Payment payment = initiate(BigDecimal.TEN);
		payment.attachProviderReference("mock_find", T1);
		payments.saveAndFlush(payment);
		payments.saveAndFlush(Payment.initiate(UUID.randomUUID(), userId, BigDecimal.TEN, "TRY",
				PaymentProviderType.MOCK, T1));
		entityManager.clear();

		assertThat(payments.findByOrderId(orderId)).get().extracting(Payment::getId).isEqualTo(payment.getId());
		assertThat(payments.findByOrderId(UUID.randomUUID())).isEmpty();
		assertThat(payments.findByProviderTypeAndProviderPaymentId(PaymentProviderType.MOCK, "mock_find")).get()
			.extracting(Payment::getId).isEqualTo(payment.getId());
		assertThat(payments.findByProviderTypeAndProviderPaymentId(PaymentProviderType.STRIPE, "mock_find")).isEmpty();
		assertThat(payments.findByProviderTypeAndProviderPaymentId(PaymentProviderType.MOCK, "MOCK_FIND"))
			.as("referans büyük/küçük harfe duyarlı: kolon utf8mb4_bin (V2)").isEmpty();
	}

	@Test
	void referencesDifferingOnlyInCaseAreDistinctPayments() {
		Payment lower = initiate(BigDecimal.TEN);
		lower.attachProviderReference("mock_case", T1);
		payments.saveAndFlush(lower);
		Payment upper = Payment.initiate(UUID.randomUUID(), userId, BigDecimal.TEN, "TRY", PaymentProviderType.MOCK, T1);
		upper.attachProviderReference("MOCK_CASE", T1);
		payments.saveAndFlush(upper);
		entityManager.clear();

		assertThat(payments.findByProviderTypeAndProviderPaymentId(PaymentProviderType.MOCK, "mock_case")).get()
			.extracting(Payment::getId).isEqualTo(lower.getId());
		assertThat(payments.findByProviderTypeAndProviderPaymentId(PaymentProviderType.MOCK, "MOCK_CASE")).get()
			.extracting(Payment::getId).isEqualTo(upper.getId());
	}

	@Test
	void secondPaymentForSameOrderViolatesUkPaymentsOrder() {
		payments.saveAndFlush(initiate(BigDecimal.TEN));

		Throwable thrown = catchThrowable(() -> payments.saveAndFlush(initiate(BigDecimal.ONE)));

		assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(DbConstraints.isViolated(thrown, "uk_payments_order")).isTrue();
	}

	@Test
	void succeedIsPersistedWithClockStamp() {
		Payment payment = payments.saveAndFlush(initiate(BigDecimal.TEN));

		assertThat(payment.succeed(T2)).isEqualTo(TransitionResult.APPLIED);
		payments.flush();

		assertThat(dbRow(payment.getId())).containsEntry("s", "succeeded")
			.containsEntry("f", null)
			.containsEntry("updated", "2026-03-01 10:16:00.000001")
			.containsEntry("created", "2026-03-01 10:15:30.123456");
	}

	@Test
	void failIsPersistedWithCodeAndClockStamp() {
		Payment payment = payments.saveAndFlush(initiate(BigDecimal.TEN));

		assertThat(payment.fail("CARD_DECLINED", T2)).isEqualTo(TransitionResult.APPLIED);
		payments.flush();

		assertThat(dbRow(payment.getId())).containsEntry("s", "failed")
			.containsEntry("f", "CARD_DECLINED")
			.containsEntry("updated", "2026-03-01 10:16:00.000001");
	}

	@Test
	void repeatedOrConflictingResultWritesNothing() {
		Payment payment = payments.saveAndFlush(initiate(BigDecimal.TEN));
		payment.fail("CARD_DECLINED", T2);
		payments.flush();
		Clock later = Clock.offset(T2, java.time.Duration.ofMinutes(5));

		assertThat(payment.fail("CARD_DECLINED", later)).isEqualTo(TransitionResult.ALREADY_IN_STATE);
		assertThat(payment.succeed(later)).isEqualTo(TransitionResult.CONFLICTING_FINAL);
		payments.flush();

		assertThat(dbRow(payment.getId())).containsEntry("s", "failed")
			.containsEntry("f", "CARD_DECLINED")
			.containsEntry("updated", "2026-03-01 10:16:00.000001");
	}

	@Test
	void providerEventIsStoredAndReadBack() {
		Payment payment = payments.saveAndFlush(initiate(BigDecimal.TEN));
		ProviderEvent event = events.saveAndFlush(ProviderEvent.record(PaymentProviderType.MOCK, "evt_1",
				payment.getId(), ProviderEventType.PAYMENT_SUCCEEDED, T1));
		entityManager.clear();

		assertThat(event.getId().version()).isEqualTo(7);
		Map<String, Object> row = jdbc.queryForMap("""
				SELECT provider AS p, provider_event_id AS e, BIN_TO_UUID(payment_id) AS pay, event_type AS t,
					DATE_FORMAT(processed_at, '%Y-%m-%d %H:%i:%s.%f') AS at
				FROM provider_events WHERE id = ?
				""", (Object) bytes(event.getId()));
		assertThat(row).containsEntry("p", "mock")
			.containsEntry("e", "evt_1")
			.containsEntry("pay", payment.getId().toString())
			.containsEntry("t", "payment.succeeded")
			.containsEntry("at", "2026-03-01 10:15:30.123456");

		ProviderEvent loaded = events.findById(event.getId()).orElseThrow();
		assertThat(loaded.getEventType()).isEqualTo(ProviderEventType.PAYMENT_SUCCEEDED);
		assertThat(loaded.getProviderType()).isEqualTo(PaymentProviderType.MOCK);
		assertThat(loaded.getPaymentId()).isEqualTo(payment.getId());
	}

	@Test
	void sameProviderEventTwiceViolatesUniqueThroughJpa() {
		Payment payment = payments.saveAndFlush(initiate(BigDecimal.TEN));
		events.saveAndFlush(ProviderEvent.record(PaymentProviderType.MOCK, "evt_dup", payment.getId(),
				ProviderEventType.PAYMENT_SUCCEEDED, T1));

		Throwable thrown = catchThrowable(() -> events.saveAndFlush(ProviderEvent.record(PaymentProviderType.MOCK,
				"evt_dup", payment.getId(), ProviderEventType.PAYMENT_FAILED, T2)));

		assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(DbConstraints.isViolated(thrown, "uk_provider_events_provider_event")).isTrue();
	}

	@Test
	void eventForMissingPaymentViolatesForeignKeyThroughJpa() {
		Throwable thrown = catchThrowable(() -> events.saveAndFlush(ProviderEvent.record(PaymentProviderType.MOCK,
				"evt_orphan", UUID.randomUUID(), ProviderEventType.PAYMENT_FAILED, T1)));

		assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(DbConstraints.isViolated(thrown, "fk_provider_events_payment")).isTrue();
	}

	private Payment initiate(BigDecimal amount) {
		return Payment.initiate(orderId, userId, amount, "TRY", PaymentProviderType.MOCK, T1);
	}

	private Map<String, Object> dbRow(UUID id) {
		return jdbc.queryForMap("""
				SELECT status AS s, failure_code AS f,
					DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created,
					DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') AS updated
				FROM payments WHERE id = ?
				""", (Object) bytes(id));
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16)
			.putLong(id.getMostSignificantBits())
			.putLong(id.getLeastSignificantBits())
			.array();
	}

}
