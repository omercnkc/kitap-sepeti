package com.kitapsepeti.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentTest {

	private static final Clock T1 = Clock.fixed(Instant.parse("2026-03-01T10:15:30.123456789Z"), ZoneOffset.UTC);

	private static final Clock T2 = Clock.fixed(Instant.parse("2026-03-01T10:16:00Z"), ZoneOffset.UTC);

	private static final Clock T3 = Clock.fixed(Instant.parse("2026-03-01T10:17:00Z"), ZoneOffset.UTC);

	private static final Instant T1_MICROS = Instant.parse("2026-03-01T10:15:30.123456Z");

	private final UUID orderId = UUID.randomUUID();

	private final UUID userId = UUID.randomUUID();

	// --- initiate ---

	@Test
	void initiateCreatesInitiatedPaymentWithoutReferenceAndClockTimestamps() {
		Payment payment = Payment.initiate(orderId, userId, new BigDecimal("10.5"), "TRY", PaymentProviderType.MOCK, T1);

		assertThat(payment.getId()).as("assigned on persist").isNull();
		assertThat(payment.getOrderId()).isEqualTo(orderId);
		assertThat(payment.getUserId()).isEqualTo(userId);
		assertThat(payment.getAmount()).isEqualTo(new BigDecimal("10.50"));
		assertThat(payment.getCurrency()).isEqualTo("TRY");
		assertThat(payment.getProviderType()).isEqualTo(PaymentProviderType.MOCK);
		assertThat(payment.getProviderPaymentId()).isNull();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
		assertThat(payment.getFailureCode()).isNull();
		assertThat(payment.getCreatedAt()).isEqualTo(T1_MICROS);
		assertThat(payment.getUpdatedAt()).isEqualTo(T1_MICROS);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.00", "-1", "10.001", "10000000000.00" })
	void initiateRejectsInvalidAmount(String amount) {
		assertThatThrownBy(() -> initiate(new BigDecimal(amount), "TRY")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void initiateAcceptsAmountBounds() {
		assertThat(initiate(new BigDecimal("0.01"), "TRY").getAmount()).isEqualTo(new BigDecimal("0.01"));
		assertThat(initiate(Payment.MAX_AMOUNT, "TRY").getAmount()).isEqualTo(Payment.MAX_AMOUNT);
		assertThat(initiate(new BigDecimal("10.000"), "TRY").getAmount()).isEqualTo(new BigDecimal("10.00"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "try", "Try", "TR", "TRYY", "T1Y", " TRY" })
	void initiateRejectsInvalidCurrency(String currency) {
		assertThatThrownBy(() -> initiate(BigDecimal.TEN, currency)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void initiateRequiresIdsProviderAndAmount() {
		assertThatThrownBy(() -> Payment.initiate(null, userId, BigDecimal.TEN, "TRY", PaymentProviderType.MOCK, T1))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Payment.initiate(orderId, null, BigDecimal.TEN, "TRY", PaymentProviderType.MOCK, T1))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Payment.initiate(orderId, userId, null, "TRY", PaymentProviderType.MOCK, T1))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Payment.initiate(orderId, userId, BigDecimal.TEN, "TRY", null, T1))
			.isInstanceOf(NullPointerException.class);
	}

	// --- attachProviderReference ---

	@Test
	void attachProviderReferenceStoresReferenceAndStamps() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");

		assertThat(payment.attachProviderReference("mock_1", T2)).isTrue();

		assertThat(payment.getProviderPaymentId()).isEqualTo("mock_1");
		assertThat(payment.getUpdatedAt()).isEqualTo(T2.instant());
		assertThat(payment.getCreatedAt()).isEqualTo(T1_MICROS);
	}

	@Test
	void attachingSameReferenceAgainChangesNothing() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");
		payment.attachProviderReference("mock_1", T2);

		assertThat(payment.attachProviderReference("mock_1", T3)).isFalse();

		assertThat(payment.getUpdatedAt()).isEqualTo(T2.instant());
	}

	@Test
	void attachingAnotherReferenceIsRejected() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");
		payment.attachProviderReference("mock_1", T2);

		assertThatThrownBy(() -> payment.attachProviderReference("mock_2", T3))
			.isInstanceOf(IllegalStateException.class);
		assertThat(payment.getProviderPaymentId()).isEqualTo("mock_1");
		assertThat(payment.getUpdatedAt()).isEqualTo(T2.instant());
	}

	@Test
	void attachingReferenceToFinishedPaymentIsRejected() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");
		payment.succeed(T2);

		assertThatThrownBy(() -> payment.attachProviderReference("mock_1", T3))
			.isInstanceOf(IllegalStateException.class);
		assertThat(payment.getProviderPaymentId()).isNull();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "\t" })
	void attachRejectsBlankReference(String reference) {
		Payment payment = initiate(BigDecimal.TEN, "TRY");

		assertThatThrownBy(() -> payment.attachProviderReference(reference, T2))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(payment.getProviderPaymentId()).isNull();
	}

	@Test
	void attachAcceptsReferenceUpTo128Characters() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");

		assertThatThrownBy(() -> payment.attachProviderReference("r".repeat(129), T2))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(payment.attachProviderReference("r".repeat(128), T2)).isTrue();
	}

	// --- succeed ---

	@Test
	void succeedFromInitiatedIsAppliedAndStamped() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");

		assertThat(payment.succeed(T2)).isEqualTo(TransitionResult.APPLIED);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
		assertThat(payment.getFailureCode()).isNull();
		assertThat(payment.getUpdatedAt()).isEqualTo(T2.instant());
	}

	@Test
	void succeedAgainIsAlreadyInStateWithoutStamp() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");
		payment.succeed(T2);

		assertThat(payment.succeed(T3)).isEqualTo(TransitionResult.ALREADY_IN_STATE);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
		assertThat(payment.getUpdatedAt()).isEqualTo(T2.instant());
	}

	@Test
	void succeedOnFailedPaymentConflictsAndChangesNothing() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");
		payment.fail("CARD_DECLINED", T2);

		assertThat(payment.succeed(T3)).isEqualTo(TransitionResult.CONFLICTING_FINAL);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(payment.getFailureCode()).isEqualTo("CARD_DECLINED");
		assertThat(payment.getUpdatedAt()).isEqualTo(T2.instant());
	}

	// --- fail ---

	@Test
	void failFromInitiatedIsAppliedWithCodeAndStamped() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");

		assertThat(payment.fail("CARD_DECLINED", T2)).isEqualTo(TransitionResult.APPLIED);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(payment.getFailureCode()).isEqualTo("CARD_DECLINED");
		assertThat(payment.getUpdatedAt()).isEqualTo(T2.instant());
	}

	@Test
	void failAgainWithSameCodeIsAlreadyInStateWithoutStamp() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");
		payment.fail("CARD_DECLINED", T2);

		assertThat(payment.fail("CARD_DECLINED", T3)).isEqualTo(TransitionResult.ALREADY_IN_STATE);

		assertThat(payment.getUpdatedAt()).isEqualTo(T2.instant());
	}

	@Test
	void failAgainWithOtherCodeConflictsAndKeepsFirstCode() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");
		payment.fail("CARD_DECLINED", T2);

		assertThat(payment.fail("INSUFFICIENT_FUNDS", T3)).isEqualTo(TransitionResult.CONFLICTING_FINAL);

		assertThat(payment.getFailureCode()).isEqualTo("CARD_DECLINED");
		assertThat(payment.getUpdatedAt()).isEqualTo(T2.instant());
	}

	@Test
	void failOnSucceededPaymentConflictsAndChangesNothing() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");
		payment.succeed(T2);

		assertThat(payment.fail("CARD_DECLINED", T3)).isEqualTo(TransitionResult.CONFLICTING_FINAL);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
		assertThat(payment.getFailureCode()).isNull();
		assertThat(payment.getUpdatedAt()).isEqualTo(T2.instant());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "card_declined", "Card_Declined", "1CARD", "_CARD", "CARD-DECLINED", "CARD DECLINED" })
	void failRejectsInvalidCodeFormat(String code) {
		Payment payment = initiate(BigDecimal.TEN, "TRY");

		assertThatThrownBy(() -> payment.fail(code, T2)).isInstanceOf(IllegalArgumentException.class);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
		assertThat(payment.getUpdatedAt()).isEqualTo(T1_MICROS);
	}

	@Test
	void failCodeMayBeUpTo64Characters() {
		assertThatThrownBy(() -> initiate(BigDecimal.TEN, "TRY").fail("A".repeat(65), T2))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(initiate(BigDecimal.TEN, "TRY").fail("A".repeat(64), T2)).isEqualTo(TransitionResult.APPLIED);
		assertThat(initiate(BigDecimal.TEN, "TRY").fail("E", T2)).isEqualTo(TransitionResult.APPLIED);
	}

	@Test
	void invalidCodeIsRejectedEvenOnFinishedPayment() {
		Payment payment = initiate(BigDecimal.TEN, "TRY");
		payment.succeed(T2);

		assertThatThrownBy(() -> payment.fail("declined", T3)).isInstanceOf(IllegalArgumentException.class);
	}

	// --- matches ---

	@Test
	void matchesSameValuesComparingAmountByValue() {
		Payment payment = initiate(new BigDecimal("10.00"), "TRY");

		assertThat(payment.matches(userId, new BigDecimal("10.00"), "TRY")).isTrue();
		assertThat(payment.matches(userId, new BigDecimal("10.0"), "TRY")).isTrue();
		assertThat(payment.matches(userId, BigDecimal.TEN, "TRY")).isTrue();
	}

	@Test
	void doesNotMatchDifferentValues() {
		Payment payment = initiate(new BigDecimal("10.00"), "TRY");

		assertThat(payment.matches(UUID.randomUUID(), new BigDecimal("10.00"), "TRY")).isFalse();
		assertThat(payment.matches(userId, new BigDecimal("10.01"), "TRY")).isFalse();
		assertThat(payment.matches(userId, new BigDecimal("10.00"), "USD")).isFalse();
		assertThat(payment.matches(userId, null, "TRY")).isFalse();
		assertThat(payment.matches(null, new BigDecimal("10.00"), "TRY")).isFalse();
		assertThat(payment.matches(userId, new BigDecimal("10.00"), null)).isFalse();
	}

	private Payment initiate(BigDecimal amount, String currency) {
		return Payment.initiate(orderId, userId, amount, currency, PaymentProviderType.MOCK, T1);
	}

}
