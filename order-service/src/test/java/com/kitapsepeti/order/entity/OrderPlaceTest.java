package com.kitapsepeti.order.entity;

import static com.kitapsepeti.order.entity.OrderFixtures.T1;
import static com.kitapsepeti.order.entity.OrderFixtures.address;
import static com.kitapsepeti.order.entity.OrderFixtures.line;
import static com.kitapsepeti.order.entity.OrderFixtures.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.order.entity.OrderRuleViolation.Code;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link Order#place}: tutar hesapları, kurallar ve başlangıç durumu (DB'siz). */
class OrderPlaceTest {

	private static final Instant T1_MICROS = Instant.parse("2026-03-01T10:15:30.123456Z");

	@Test
	void computesLineTotalsSubtotalAndTotalWithScaleTwo() {
		OrderLine a = line("10.5", 2);
		OrderLine free = line("0", 1);
		OrderLine c = line("7.30", 3);

		Order order = place(a, free, c);

		assertThat(order.getItems()).extracting(OrderItem::getBookId, i -> i.getUnitPrice().toPlainString(),
				OrderItem::getQuantity, i -> i.getLineTotal().toPlainString())
			.containsExactly(tuple(a.bookId(), "10.50", 2, "21.00"), tuple(free.bookId(), "0.00", 1, "0.00"),
					tuple(c.bookId(), "7.30", 3, "21.90"));
		assertThat(order.getSubtotal().toPlainString()).isEqualTo("42.90");
		assertThat(order.getDiscountAmount().toPlainString()).isEqualTo("0.00");
		assertThat(order.getTotalAmount().toPlainString()).isEqualTo("42.90");
	}

	@Test
	void freeLineWithPaidLineIsAccepted() {
		Order order = place(line("0.00", 2), line("0.01", 1));

		assertThat(order.getTotalAmount().toPlainString()).isEqualTo("0.01");
	}

	/** 10.000 değer olarak 2 ondalığa sığar (yuvarlama yok); 10.001 sığmaz. */
	@Test
	void trailingZerosBeyondScaleTwoAreNormalized() {
		Order order = place(line("10.000", 1));

		assertThat(order.getItems().get(0).getUnitPrice().toPlainString()).isEqualTo("10.00");
	}

	@Test
	void copiesSnapshotFieldsAndStartsPendingRequested() {
		UUID userId = UUID.randomUUID();
		UUID cartId = UUID.randomUUID();
		OrderLine line = line("149.90", 1);

		Order order = Order.place(userId, cartId, "USD", List.of(line), address(), T1);

		assertThat(order.getId()).isNull();
		assertThat(order.getUserId()).isEqualTo(userId);
		assertThat(order.getCartId()).isEqualTo(cartId);
		assertThat(order.getCurrency()).isEqualTo("USD");
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
		assertThat(order.getStockState()).isEqualTo(StockState.REQUESTED);
		assertThat(order.getPaymentId()).isNull();
		assertThat(order.getFailureCode()).isNull();
		assertThat(order.getCouponCode()).isNull();
		assertThat(order.getAddressSnapshot()).isEqualTo(address());
		assertThat(order.getCreatedAt()).isEqualTo(T1_MICROS);
		assertThat(order.getUpdatedAt()).isEqualTo(T1_MICROS);
		assertThat(order.getItems()).singleElement().satisfies(item -> {
			assertThat(item.getOrder()).isSameAs(order);
			assertThat(item.getTitleSnapshot()).isEqualTo(line.title());
		});
	}

	@Test
	void writesInitialHistoryRecord() {
		Order order = place(line("149.90", 1));

		assertThat(order.getHistory()).singleElement().satisfies(history -> {
			assertThat(history.getOrder()).isSameAs(order);
			assertThat(history.getFromStatus()).isNull();
			assertThat(history.getToStatus()).isEqualTo(OrderStatus.PENDING);
			assertThat(history.getReason()).isEqualTo(OrderReasons.ORDER_PLACED);
			assertThat(history.getCreatedAt()).isEqualTo(T1_MICROS);
		});
	}

	@Test
	void itemsAndHistoryAreReadOnly() {
		Order order = place(line("149.90", 1));

		assertThatThrownBy(() -> order.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> order.getHistory().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	// --- kurallar ---

	@Test
	void emptyOrderIsRejected() {
		assertViolation(() -> place(), Code.EMPTY_ORDER);
	}

	@Test
	void duplicateBookIsRejected() {
		OrderLine first = line("10.00", 1);
		OrderLine sameBook = new OrderLine(first.bookId(), "Aynı kitap", 2, new BigDecimal("10.00"));

		assertViolation(() -> place(first, sameBook), Code.DUPLICATE_BOOK);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 100 })
	void quantityOutsideOneToNinetyNineIsRejected(int quantity) {
		assertViolation(() -> place(line("10.00", 1), line("10.00", quantity)), Code.INVALID_QUANTITY);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 99 })
	void quantityBoundsAreAccepted(int quantity) {
		assertThat(place(line("10.00", quantity)).getItems().get(0).getQuantity()).isEqualTo(quantity);
	}

	@ParameterizedTest
	@ValueSource(strings = { "-0.01", "10.001", "0.005", "10000000000.00" })
	void negativeOrOverScaledPriceIsRejected(String price) {
		assertViolation(() -> place(line(price, 1)), Code.INVALID_PRICE);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "try", "Try", "TR", "TRYY", "T1Y", "" })
	void invalidCurrencyIsRejected(String currency) {
		assertViolation(() -> Order.place(UUID.randomUUID(), UUID.randomUUID(), currency, List.of(line("10.00", 1)),
				address(), T1), Code.INVALID_CURRENCY);
	}

	/** Karar: tamamı ücretsiz sepet sipariş yazılmadan reddedilir (checkout'ta 422 ORDER_TOTAL_ZERO). */
	@Test
	void allFreeOrderIsRejected() {
		assertViolation(() -> place(line("0.00", 3), line("0", 1)), Code.ORDER_TOTAL_ZERO);
	}

	@Test
	void violationMessageCarriesOnlyTheCode() {
		assertThatThrownBy(() -> place(line("0.00", 1))).hasMessage("Order rule violated: ORDER_TOTAL_ZERO");
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   " })
	void blankTitleIsAProgrammingError(String title) {
		assertThatThrownBy(() -> place(new OrderLine(UUID.randomUUID(), title, 1, BigDecimal.TEN)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void titleLongerThanCatalogLimitIsAProgrammingError() {
		assertThat(place(new OrderLine(UUID.randomUUID(), "a".repeat(300), 1, BigDecimal.TEN)).getItems()).hasSize(1);
		assertThatThrownBy(() -> place(new OrderLine(UUID.randomUUID(), "a".repeat(301), 1, BigDecimal.TEN)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void totalAboveDecimalLimitIsRejected() {
		assertThatThrownBy(() -> place(line("9999999999.99", 2))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void requiredArgumentsMustNotBeNull() {
		List<OrderLine> lines = List.of(line("10.00", 1));
		assertThatThrownBy(() -> Order.place(null, UUID.randomUUID(), "TRY", lines, address(), T1))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Order.place(UUID.randomUUID(), null, "TRY", lines, address(), T1))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Order.place(UUID.randomUUID(), UUID.randomUUID(), "TRY", lines, null, T1))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> place(new OrderLine(UUID.randomUUID(), "Kitap", 1, null)))
			.isInstanceOf(NullPointerException.class);
	}

	private static void assertViolation(ThrowingCallable call, Code code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(OrderRuleViolation.class,
				ex -> assertThat(ex.code()).isEqualTo(code));
	}

}
