package com.kitapsepeti.order.entity;

import static com.kitapsepeti.order.entity.OrderFixtures.T1;
import static com.kitapsepeti.order.entity.OrderFixtures.T3;
import static com.kitapsepeti.order.entity.OrderFixtures.placeDefault;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Geçiş metotlarının tablo testleri (DB'siz). Her satır: başlangıç durumu → beklenen sonuç. Ortak kurallar
 * {@link #apply}'da doğrulanır: yalnızca APPLIED alanları ve {@code updatedAt}'i değiştirir; yalnızca durum geçişleri
 * geçmiş yazar; APPLIED olmayan sonuç hiçbir alanı değiştirmez.
 */
class OrderTransitionsTest {

	private static final UUID PAYMENT = UUID.fromString("0190a000-0000-7000-8000-000000000001");

	private static final UUID OTHER_PAYMENT = UUID.fromString("0190a000-0000-7000-8000-000000000002");

	/** Ulaşılabilir başlangıç durumları; hazırlık T1 ile, test edilen geçiş T3 ile çalışır. */
	enum Start {

		PENDING_REQUESTED(OrderStatus.PENDING, StockState.REQUESTED, null),
		PENDING_REQUESTED_WITH_PAYMENT(OrderStatus.PENDING, StockState.REQUESTED, PAYMENT),
		PENDING_HELD(OrderStatus.PENDING, StockState.HELD, null),
		PENDING_HELD_WITH_PAYMENT(OrderStatus.PENDING, StockState.HELD, PAYMENT),
		PAID_HELD(OrderStatus.PAID, StockState.HELD, PAYMENT),
		PAID_COMMITTED(OrderStatus.PAID, StockState.COMMITTED, PAYMENT),
		PAID_LOST(OrderStatus.PAID, StockState.LOST, PAYMENT),
		FAILED_REQUESTED(OrderStatus.FAILED, StockState.REQUESTED, null),
		FAILED_HELD(OrderStatus.FAILED, StockState.HELD, null),
		FAILED_HELD_WITH_PAYMENT(OrderStatus.FAILED, StockState.HELD, PAYMENT),
		FAILED_RELEASED(OrderStatus.FAILED, StockState.RELEASED, null);

		final OrderStatus status;

		final StockState stock;

		final UUID payment;

		Start(OrderStatus status, StockState stock, UUID payment) {
			this.status = status;
			this.stock = stock;
			this.payment = payment;
		}

		Order build() {
			Order order = placeDefault();
			if (payment != null) {
				assertThat(order.attachPayment(payment, T1)).isEqualTo(TransitionResult.APPLIED);
			}
			if (stock != StockState.REQUESTED) {
				assertThat(order.markStockHeld(T1)).isEqualTo(TransitionResult.APPLIED);
			}
			if (status == OrderStatus.PAID) {
				assertThat(order.markPaid(payment, T1)).isEqualTo(TransitionResult.APPLIED);
				if (stock == StockState.COMMITTED) {
					assertThat(order.markStockCommitted(T1)).isEqualTo(TransitionResult.APPLIED);
				}
				if (stock == StockState.LOST) {
					assertThat(order.markStockLost(T1)).isEqualTo(TransitionResult.APPLIED);
				}
			}
			if (status == OrderStatus.FAILED) {
				assertThat(order.markFailed(OrderReasons.OUT_OF_STOCK, T1)).isEqualTo(TransitionResult.APPLIED);
				if (stock == StockState.RELEASED) {
					assertThat(order.markStockReleased(T1)).isEqualTo(TransitionResult.APPLIED);
				}
			}
			assertThat(order.getStatus()).isEqualTo(status);
			assertThat(order.getStockState()).isEqualTo(stock);
			assertThat(order.getPaymentId()).isEqualTo(payment);
			return order;
		}

	}

	// --- markStockHeld ---

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({ "PENDING_REQUESTED, APPLIED", "PENDING_HELD, ALREADY_IN_STATE", "PAID_HELD, ALREADY_IN_STATE",
			"PAID_COMMITTED, CONFLICTING_FINAL", "PAID_LOST, CONFLICTING_FINAL", "FAILED_REQUESTED, APPLIED",
			"FAILED_HELD, ALREADY_IN_STATE", "FAILED_RELEASED, CONFLICTING_FINAL" })
	void markStockHeld(Start start, TransitionResult expected) {
		Order order = start.build();

		apply(order, o -> o.markStockHeld(T3), expected, false);

		if (expected == TransitionResult.APPLIED) {
			assertThat(order.getStockState()).isEqualTo(StockState.HELD);
		}
	}

	// --- attachPayment ---

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({ "PENDING_REQUESTED, APPLIED", "PENDING_HELD, APPLIED", "FAILED_REQUESTED, APPLIED",
			"FAILED_HELD, APPLIED", "FAILED_RELEASED, APPLIED", "PENDING_REQUESTED_WITH_PAYMENT, ALREADY_IN_STATE",
			"PENDING_HELD_WITH_PAYMENT, ALREADY_IN_STATE", "PAID_HELD, ALREADY_IN_STATE",
			"PAID_COMMITTED, ALREADY_IN_STATE", "PAID_LOST, ALREADY_IN_STATE",
			"FAILED_HELD_WITH_PAYMENT, ALREADY_IN_STATE" })
	void attachSamePayment(Start start, TransitionResult expected) {
		Order order = start.build();

		apply(order, o -> o.attachPayment(PAYMENT, T3), expected, false);

		assertThat(order.getPaymentId()).isEqualTo(PAYMENT);
	}

	@ParameterizedTest(name = "{0} → CONFLICTING_FINAL")
	@ValueSource(strings = { "PENDING_REQUESTED_WITH_PAYMENT", "PENDING_HELD_WITH_PAYMENT", "PAID_HELD",
			"PAID_COMMITTED", "PAID_LOST", "FAILED_HELD_WITH_PAYMENT" })
	void attachDifferentPaymentConflicts(Start start) {
		Order order = start.build();

		apply(order, o -> o.attachPayment(OTHER_PAYMENT, T3), TransitionResult.CONFLICTING_FINAL, false);

		assertThat(order.getPaymentId()).isEqualTo(PAYMENT);
	}

	// --- markPaid ---

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({ "PENDING_HELD, APPLIED", "PENDING_HELD_WITH_PAYMENT, APPLIED", "PAID_HELD, ALREADY_IN_STATE",
			"PAID_COMMITTED, ALREADY_IN_STATE", "PAID_LOST, ALREADY_IN_STATE", "FAILED_REQUESTED, CONFLICTING_FINAL",
			"FAILED_HELD, CONFLICTING_FINAL", "FAILED_HELD_WITH_PAYMENT, CONFLICTING_FINAL",
			"FAILED_RELEASED, CONFLICTING_FINAL" })
	void markPaid(Start start, TransitionResult expected) {
		Order order = start.build();

		apply(order, o -> o.markPaid(PAYMENT, T3), expected, true);

		if (expected == TransitionResult.APPLIED) {
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
			assertThat(order.getPaymentId()).isEqualTo(PAYMENT);
			assertLastHistory(order, OrderStatus.PENDING, OrderStatus.PAID, OrderReasons.PAYMENT_SUCCEEDED);
		}
	}

	@ParameterizedTest(name = "{0} → CONFLICTING_FINAL")
	@ValueSource(strings = { "PENDING_HELD_WITH_PAYMENT", "PAID_HELD", "PAID_COMMITTED", "PAID_LOST" })
	void markPaidWithDifferentPaymentConflicts(Start start) {
		Order order = start.build();

		apply(order, o -> o.markPaid(OTHER_PAYMENT, T3), TransitionResult.CONFLICTING_FINAL, true);
	}

	@ParameterizedTest(name = "{0} → IllegalStateException")
	@ValueSource(strings = { "PENDING_REQUESTED", "PENDING_REQUESTED_WITH_PAYMENT" })
	void markPaidBeforeStockIsHeldIsAProgrammingError(Start start) {
		Order order = start.build();
		Snapshot before = Snapshot.of(order);

		assertThatThrownBy(() -> order.markPaid(PAYMENT, T3)).isInstanceOf(IllegalStateException.class);
		assertThat(Snapshot.of(order)).isEqualTo(before);
	}

	// --- markFailed ---

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({ "PENDING_REQUESTED, APPLIED", "PENDING_REQUESTED_WITH_PAYMENT, APPLIED", "PENDING_HELD, APPLIED",
			"PENDING_HELD_WITH_PAYMENT, APPLIED", "PAID_HELD, CONFLICTING_FINAL", "PAID_COMMITTED, CONFLICTING_FINAL",
			"PAID_LOST, CONFLICTING_FINAL", "FAILED_REQUESTED, ALREADY_IN_STATE", "FAILED_HELD, ALREADY_IN_STATE",
			"FAILED_HELD_WITH_PAYMENT, ALREADY_IN_STATE", "FAILED_RELEASED, ALREADY_IN_STATE" })
	void markFailed(Start start, TransitionResult expected) {
		Order order = start.build();

		apply(order, o -> o.markFailed(OrderReasons.ORDER_EXPIRED, T3), expected, true);

		if (expected == TransitionResult.APPLIED) {
			assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
			assertThat(order.getFailureCode()).isEqualTo(OrderReasons.ORDER_EXPIRED);
			assertThat(order.getStockState()).isEqualTo(start.stock);
			assertLastHistory(order, OrderStatus.PENDING, OrderStatus.FAILED, OrderReasons.ORDER_EXPIRED);
		}
	}

	@Test
	void repeatedFailureKeepsTheFirstCode() {
		Order order = placeDefault();
		assertThat(order.markFailed(OrderReasons.OUT_OF_STOCK, OrderFixtures.T2)).isEqualTo(TransitionResult.APPLIED);

		assertThat(order.markFailed(OrderReasons.CARD_DECLINED, T3)).isEqualTo(TransitionResult.ALREADY_IN_STATE);

		assertThat(order.getFailureCode()).isEqualTo(OrderReasons.OUT_OF_STOCK);
		assertThat(order.getHistory()).extracting(OrderStatusHistory::getReason)
			.containsExactly(OrderReasons.ORDER_PLACED, OrderReasons.OUT_OF_STOCK);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "order_expired", "Card_DECLINED", "1CODE", "_CODE", "CODE-1", "CODE 1",
			"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" })
	void invalidFailureCodeIsAProgrammingError(String code) {
		Order order = placeDefault();
		Snapshot before = Snapshot.of(order);

		assertThatThrownBy(() -> order.markFailed(code, T3)).isInstanceOf(IllegalArgumentException.class);
		assertThat(Snapshot.of(order)).isEqualTo(before);
	}

	@Test
	void failureCodeOfSixtyFourCharactersIsAccepted() {
		String code = "A".repeat(64);
		Order order = placeDefault();

		assertThat(order.markFailed(code, T3)).isEqualTo(TransitionResult.APPLIED);
		assertThat(order.getFailureCode()).isEqualTo(code);
	}

	/** Kod biçimi durumdan bağımsız doğrulanır: ödenmiş siparişte bile geçersiz kod IAE'dir. */
	@Test
	void invalidFailureCodeIsRejectedInAnyState() {
		Order order = Start.PAID_HELD.build();

		assertThatThrownBy(() -> order.markFailed("bad", T3)).isInstanceOf(IllegalArgumentException.class);
	}

	// --- markStockCommitted ---

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({ "PENDING_REQUESTED, CONFLICTING_FINAL", "PENDING_HELD, CONFLICTING_FINAL",
			"PENDING_HELD_WITH_PAYMENT, CONFLICTING_FINAL", "PAID_HELD, APPLIED", "PAID_COMMITTED, ALREADY_IN_STATE",
			"PAID_LOST, CONFLICTING_FINAL", "FAILED_REQUESTED, CONFLICTING_FINAL", "FAILED_HELD, CONFLICTING_FINAL",
			"FAILED_RELEASED, CONFLICTING_FINAL" })
	void markStockCommitted(Start start, TransitionResult expected) {
		Order order = start.build();

		apply(order, o -> o.markStockCommitted(T3), expected, false);

		if (expected == TransitionResult.APPLIED) {
			assertThat(order.getStockState()).isEqualTo(StockState.COMMITTED);
		}
	}

	// --- markStockReleased ---

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({ "PENDING_REQUESTED, CONFLICTING_FINAL", "PENDING_HELD, CONFLICTING_FINAL",
			"PAID_HELD, CONFLICTING_FINAL", "PAID_COMMITTED, CONFLICTING_FINAL", "PAID_LOST, CONFLICTING_FINAL",
			"FAILED_REQUESTED, APPLIED", "FAILED_HELD, APPLIED", "FAILED_HELD_WITH_PAYMENT, APPLIED",
			"FAILED_RELEASED, ALREADY_IN_STATE" })
	void markStockReleased(Start start, TransitionResult expected) {
		Order order = start.build();

		apply(order, o -> o.markStockReleased(T3), expected, false);

		if (expected == TransitionResult.APPLIED) {
			assertThat(order.getStockState()).isEqualTo(StockState.RELEASED);
		}
	}

	// --- markStockLost ---

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({ "PENDING_REQUESTED, CONFLICTING_FINAL", "PENDING_REQUESTED_WITH_PAYMENT, CONFLICTING_FINAL",
			"PENDING_HELD, CONFLICTING_FINAL", "PENDING_HELD_WITH_PAYMENT, CONFLICTING_FINAL", "PAID_HELD, APPLIED",
			"PAID_COMMITTED, CONFLICTING_FINAL", "PAID_LOST, ALREADY_IN_STATE", "FAILED_REQUESTED, CONFLICTING_FINAL",
			"FAILED_HELD, CONFLICTING_FINAL", "FAILED_HELD_WITH_PAYMENT, CONFLICTING_FINAL",
			"FAILED_RELEASED, CONFLICTING_FINAL" })
	void markStockLost(Start start, TransitionResult expected) {
		Order order = start.build();

		apply(order, o -> o.markStockLost(T3), expected, false);

		if (expected == TransitionResult.APPLIED) {
			assertThat(order.getStockState()).isEqualTo(StockState.LOST);
		}
	}

	// --- ortak ---

	@Test
	void appliedTransitionsTruncateUpdatedAtToMicros() {
		Order order = placeDefault();

		order.markStockHeld(T3);

		assertThat(order.getUpdatedAt()).isEqualTo(Instant.parse("2026-03-01T10:17:00.000002Z"));
		assertThat(order.getCreatedAt()).isEqualTo(Instant.parse("2026-03-01T10:15:30.123456Z"));
	}

	/**
	 * Geçişi uygular ve ortak kuralları doğrular: APPLIED → {@code updatedAt} = T3, durum geçişiyse tam bir geçmiş satırı
	 * (T3); diğer sonuçlar → hiçbir alan değişmez.
	 */
	private static void apply(Order order, Function<Order, TransitionResult> transition, TransitionResult expected,
			boolean statusTransition) {
		Snapshot before = Snapshot.of(order);

		TransitionResult result = transition.apply(order);

		assertThat(result).isEqualTo(expected);
		Snapshot after = Snapshot.of(order);
		if (result == TransitionResult.APPLIED) {
			assertThat(after.updatedAt()).isEqualTo(OrderFixtures.T3.instant());
			assertThat(after.historySize()).isEqualTo(before.historySize() + (statusTransition ? 1 : 0));
			if (statusTransition) {
				assertThat(order.getHistory().get(after.historySize() - 1).getCreatedAt())
					.isEqualTo(OrderFixtures.T3.instant());
			}
			else {
				assertThat(after.status()).isEqualTo(before.status());
			}
		}
		else {
			assertThat(after).isEqualTo(before);
		}
	}

	private static void assertLastHistory(Order order, OrderStatus from, OrderStatus to, String reason) {
		OrderStatusHistory last = order.getHistory().get(order.getHistory().size() - 1);
		assertThat(last.getOrder()).isSameAs(order);
		assertThat(last.getFromStatus()).isEqualTo(from);
		assertThat(last.getToStatus()).isEqualTo(to);
		assertThat(last.getReason()).isEqualTo(reason);
	}

	private record Snapshot(OrderStatus status, StockState stock, UUID paymentId, String failureCode,
			Instant updatedAt, int historySize) {

		static Snapshot of(Order order) {
			return new Snapshot(order.getStatus(), order.getStockState(), order.getPaymentId(), order.getFailureCode(),
					order.getUpdatedAt(), order.getHistory().size());
		}

	}

}
