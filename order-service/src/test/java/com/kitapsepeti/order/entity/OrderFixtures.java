package com.kitapsepeti.order.entity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Domain testlerinin ortak girdileri: sabit saatler, adres ve geçerli satırlar. */
final class OrderFixtures {

	static final Clock T1 = Clock.fixed(Instant.parse("2026-03-01T10:15:30.123456789Z"), ZoneOffset.UTC);

	static final Clock T2 = Clock.fixed(Instant.parse("2026-03-01T10:16:00.000001Z"), ZoneOffset.UTC);

	static final Clock T3 = Clock.fixed(Instant.parse("2026-03-01T10:17:00.000002Z"), ZoneOffset.UTC);

	static final String RECIPIENT = "Gizli Alıcı Adı";

	static final String PHONE = "+905551112233";

	static final String LINE1 = "Gizli Sokak No 42";

	static final String CITY = "Eskişehir";

	private OrderFixtures() {
	}

	static AddressSnapshot address() {
		return new AddressSnapshot(RECIPIENT, PHONE, LINE1, "Daire 7", "Tepebaşı", CITY, "26000", "TR");
	}

	static AddressSnapshot minimalAddress() {
		return new AddressSnapshot(RECIPIENT, PHONE, LINE1, null, null, CITY, null, "TR");
	}

	static OrderLine line(String unitPrice, int quantity) {
		return new OrderLine(UUID.randomUUID(), "Kitap " + unitPrice, quantity, new BigDecimal(unitPrice));
	}

	static Order place(OrderLine... lines) {
		return Order.place(UUID.randomUUID(), UUID.randomUUID(), "TRY", List.of(lines), address(), T1);
	}

	static Order placeDefault() {
		return place(line("149.90", 1));
	}

}
