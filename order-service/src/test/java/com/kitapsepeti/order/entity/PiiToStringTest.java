package com.kitapsepeti.order.entity;

import static com.kitapsepeti.order.entity.OrderFixtures.CITY;
import static com.kitapsepeti.order.entity.OrderFixtures.LINE1;
import static com.kitapsepeti.order.entity.OrderFixtures.PHONE;
import static com.kitapsepeti.order.entity.OrderFixtures.RECIPIENT;
import static com.kitapsepeti.order.entity.OrderFixtures.address;
import static com.kitapsepeti.order.entity.OrderFixtures.line;
import static com.kitapsepeti.order.entity.OrderFixtures.place;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Adres, tutar ve id listesi log'a düşmesin: toString'ler bu değerleri içermez. */
class PiiToStringTest {

	private static final UUID PAYMENT = UUID.randomUUID();

	@Test
	void addressSnapshotIsRedacted() {
		assertThat(address().toString()).isEqualTo("AddressSnapshot[redacted]");
	}

	@Test
	void orderLineIsRedacted() {
		assertThat(line("123.45", 2).toString()).isEqualTo("OrderLine[redacted]");
	}

	@Test
	void entitiesDoNotPrintAddressOrAmounts() {
		Order order = place(line("123.45", 2), line("67.80", 1));
		order.markStockHeld(OrderFixtures.T2);
		order.markPaid(PAYMENT, OrderFixtures.T2);

		List<String> rendered = new ArrayList<>();
		rendered.add(order.toString());
		order.getItems().forEach(item -> rendered.add(item.toString()));
		order.getHistory().forEach(history -> rendered.add(history.toString()));

		for (String text : rendered) {
			assertThat(text).doesNotContain(RECIPIENT, PHONE, LINE1, CITY)
				.doesNotContain("123.45", "246.90", "67.80", "314.70")
				.doesNotContain(order.getUserId().toString(), order.getCartId().toString(), PAYMENT.toString());
			order.getItems().forEach(item -> assertThat(text).doesNotContain(item.getBookId().toString()));
		}
	}

}
