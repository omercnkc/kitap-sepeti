package com.kitapsepeti.cart.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/** Domain bütünlük kuralları (DB yok). */
class CartTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T10:15:30.123456789Z"), ZoneOffset.UTC);

	private final UUID userId = UUID.randomUUID();

	@Test
	void openForCreatesEmptyActiveCartWithClockTimeInMicros() {
		Cart cart = Cart.openFor(userId, CLOCK);

		assertThat(cart.getId()).isNull();
		assertThat(cart.getUserId()).isEqualTo(userId);
		assertThat(cart.getStatus()).isEqualTo(CartStatus.ACTIVE);
		assertThat(cart.getItems()).isEmpty();
		assertThat(cart.getCreatedAt()).isEqualTo("2026-03-01T10:15:30.123456Z");
		assertThat(cart.getUpdatedAt()).isEqualTo(cart.getCreatedAt());
	}

	@Test
	void addItemLinksItemToCartWithDefaults() {
		Cart cart = Cart.openFor(userId, CLOCK);
		UUID bookId = UUID.randomUUID();

		CartItem item = cart.addItem(bookId, 2, new BigDecimal("149.9"), null, "Kitap", null, CLOCK);

		assertThat(cart.getItems()).containsExactly(item);
		assertThat(item.getCart()).isSameAs(cart);
		assertThat(item.getBookId()).isEqualTo(bookId);
		assertThat(item.getQuantity()).isEqualTo(2);
		assertThat(item.getUnitPriceSnapshot()).isEqualTo(new BigDecimal("149.90"));
		assertThat(item.getCurrencySnapshot()).isEqualTo("TRY");
		assertThat(item.getCoverUrlSnapshot()).isNull();
		assertThat(item.getAddedAt()).isEqualTo("2026-03-01T10:15:30.123456Z");
		assertThat(item.getUpdatedAt()).isEqualTo(item.getAddedAt());
		assertThat(cart.findItem(bookId)).containsSame(item);
	}

	@Test
	void addingSameBookTwiceIsRejected() {
		Cart cart = Cart.openFor(userId, CLOCK);
		UUID bookId = UUID.randomUUID();
		cart.addItem(bookId, 1, BigDecimal.TEN, "TRY", "Kitap", null, CLOCK);

		assertThatThrownBy(() -> cart.addItem(bookId, 1, BigDecimal.TEN, "TRY", "Kitap", null, CLOCK))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(bookId.toString());
		assertThat(cart.getItems()).hasSize(1);
	}

	@Test
	void itemsViewIsReadOnly() {
		Cart cart = Cart.openFor(userId, CLOCK);

		assertThatThrownBy(() -> cart.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 100, -1 })
	void quantityOutsideOneToNinetyNineIsRejected(int quantity) {
		Cart cart = Cart.openFor(userId, CLOCK);
		CartItem item = cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "Kitap", null, CLOCK);

		assertThatThrownBy(() -> item.changeQuantity(quantity, CLOCK)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> cart.addItem(UUID.randomUUID(), quantity, BigDecimal.TEN, null, "Kitap", null, CLOCK))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(item.getQuantity()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 99 })
	void quantityBoundsAreAccepted(int quantity) {
		Cart cart = Cart.openFor(userId, CLOCK);
		CartItem item = cart.addItem(UUID.randomUUID(), 5, BigDecimal.TEN, null, "Kitap", null, CLOCK);

		item.changeQuantity(quantity, CLOCK);

		assertThat(item.getQuantity()).isEqualTo(quantity);
	}

	@Test
	void priceWithMoreThanTwoDecimalsIsRejectedNotRounded() {
		Cart cart = Cart.openFor(userId, CLOCK);
		CartItem item = cart.addItem(UUID.randomUUID(), 1, new BigDecimal("10"), null, "Kitap", null, CLOCK);

		assertThatThrownBy(() -> cart.addItem(UUID.randomUUID(), 1, new BigDecimal("149.999"), null, "Kitap", null, CLOCK))
			.isInstanceOf(IllegalArgumentException.class)
			.hasCauseInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> item.refreshSnapshot(new BigDecimal("149.999"), null, "Kitap", null, CLOCK))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(item.getUnitPriceSnapshot()).isEqualTo(new BigDecimal("10.00"));
	}

	@Test
	void trailingZerosBeyondScaleAreAccepted() {
		Cart cart = Cart.openFor(userId, CLOCK);

		CartItem item = cart.addItem(UUID.randomUUID(), 1, new BigDecimal("149.9000"), null, "Kitap", null, CLOCK);

		assertThat(item.getUnitPriceSnapshot()).isEqualTo(new BigDecimal("149.90"));
	}

	@Test
	void negativePriceIsRejected() {
		Cart cart = Cart.openFor(userId, CLOCK);

		assertThatThrownBy(() -> cart.addItem(UUID.randomUUID(), 1, new BigDecimal("-0.01"), null, "Kitap", null, CLOCK))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void refreshSnapshotReplacesAllSnapshotFields() {
		Cart cart = Cart.openFor(userId, CLOCK);
		CartItem item = cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, "TRY", "Eski", "https://img/eski.jpg", CLOCK);

		item.refreshSnapshot(new BigDecimal("12.5"), "EUR", "Yeni", null, CLOCK);

		assertThat(item.getUnitPriceSnapshot()).isEqualTo(new BigDecimal("12.50"));
		assertThat(item.getCurrencySnapshot()).isEqualTo("EUR");
		assertThat(item.getTitleSnapshot()).isEqualTo("Yeni");
		assertThat(item.getCoverUrlSnapshot()).isNull();
	}

	@Test
	void clearRemovesAllItems() {
		Cart cart = Cart.openFor(userId, CLOCK);
		cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, CLOCK);
		cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "B", null, CLOCK);

		cart.clear(CLOCK);

		assertThat(cart.getItems()).isEmpty();
	}

	@Test
	void unknownItemIdIsNotRemoved() {
		Cart cart = Cart.openFor(userId, CLOCK);
		cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, CLOCK);

		assertThat(cart.removeItem(UUID.randomUUID(), CLOCK)).isFalse();
		assertThat(cart.getItems()).hasSize(1);
	}

	@Test
	void checkoutOnlyFromActive() {
		Cart cart = Cart.openFor(userId, CLOCK);

		cart.checkout(CLOCK);

		assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
		assertThatThrownBy(() -> cart.checkout(CLOCK)).isInstanceOf(IllegalStateException.class)
			.hasMessage("Cart cannot move from CHECKED_OUT to CHECKED_OUT");
		assertThatThrownBy(() -> cart.abandon(CLOCK)).isInstanceOf(IllegalStateException.class);
		assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
	}

	@Test
	void abandonOnlyFromActive() {
		Cart cart = Cart.openFor(userId, CLOCK);

		cart.abandon(CLOCK);

		assertThat(cart.getStatus()).isEqualTo(CartStatus.ABANDONED);
		assertThatThrownBy(() -> cart.abandon(CLOCK)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> cart.checkout(CLOCK)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void everyChangeStampsItemAndCartWithGivenClock() {
		Cart cart = Cart.openFor(userId, CLOCK);
		CartItem item = cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, at("2026-03-01T11:00:00Z"));
		assertThat(cart.getUpdatedAt()).isEqualTo("2026-03-01T11:00:00Z");

		item.changeQuantity(2, at("2026-03-01T12:00:00Z"));
		assertThat(item.getUpdatedAt()).isEqualTo("2026-03-01T12:00:00Z");
		assertThat(cart.getUpdatedAt()).isEqualTo("2026-03-01T12:00:00Z");

		item.refreshSnapshot(BigDecimal.ONE, null, "A", null, at("2026-03-01T13:00:00Z"));
		assertThat(item.getUpdatedAt()).isEqualTo("2026-03-01T13:00:00Z");
		assertThat(cart.getUpdatedAt()).isEqualTo("2026-03-01T13:00:00Z");
		assertThat(item.getAddedAt()).isEqualTo("2026-03-01T11:00:00Z");

		cart.clear(at("2026-03-01T14:00:00Z"));
		assertThat(cart.getUpdatedAt()).isEqualTo("2026-03-01T14:00:00Z");

		cart.checkout(at("2026-03-01T15:00:00Z"));
		assertThat(cart.getUpdatedAt()).isEqualTo("2026-03-01T15:00:00Z");
		assertThat(cart.getCreatedAt()).isEqualTo("2026-03-01T10:15:30.123456Z");
	}

	@Test
	void removeItemStampsCartOnlyWhenSomethingWasRemoved() {
		Cart cart = Cart.openFor(userId, CLOCK);
		CartItem item = cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, CLOCK);
		ReflectionTestUtils.setField(item, "id", UUID.randomUUID());

		assertThat(cart.removeItem(UUID.randomUUID(), at("2026-03-01T12:00:00Z"))).isFalse();
		assertThat(cart.getUpdatedAt()).isEqualTo("2026-03-01T10:15:30.123456Z");

		assertThat(cart.removeItem(item.getId(), at("2026-03-01T13:00:00Z"))).isTrue();
		assertThat(cart.getUpdatedAt()).isEqualTo("2026-03-01T13:00:00Z");
	}

	@Test
	void rejectedChangeDoesNotStampAndHistoricCartCannotBeTouched() {
		Cart cart = Cart.openFor(userId, CLOCK);
		CartItem item = cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, CLOCK);

		assertThatThrownBy(() -> item.changeQuantity(100, at("2026-03-01T12:00:00Z")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(item.getUpdatedAt()).isEqualTo("2026-03-01T10:15:30.123456Z");
		assertThat(cart.getUpdatedAt()).isEqualTo("2026-03-01T10:15:30.123456Z");

		cart.abandon(CLOCK);
		assertThatThrownBy(() -> cart.touch(at("2026-03-01T12:00:00Z"))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void historicCartItemsCannotChange() {
		Cart cart = Cart.openFor(userId, CLOCK);
		CartItem item = cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, CLOCK);
		cart.checkout(CLOCK);

		assertThatThrownBy(() -> cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "B", null, CLOCK))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> cart.clear(CLOCK)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> cart.removeItem(UUID.randomUUID(), CLOCK)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> item.changeQuantity(2, CLOCK)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> item.refreshSnapshot(BigDecimal.ONE, null, "A", null, CLOCK))
			.isInstanceOf(IllegalStateException.class);
		assertThat(cart.getItems()).containsExactly(item);
		assertThat(item.getQuantity()).isEqualTo(1);
	}

	private static Clock at(String instant) {
		return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
	}

}
