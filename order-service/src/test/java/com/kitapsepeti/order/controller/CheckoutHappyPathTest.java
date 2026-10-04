package com.kitapsepeti.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.order.client.InternalApiKey;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checkout mutlu yolu: 201 + Location, sipariş {@code pending} + {@code held} + ödeme bağlı, kalemler Catalog
 * fiyatı/başlığı ve Cart adediyle; alt servislere giden gövdeler ve başlıklar.
 */
class CheckoutHappyPathTest extends CheckoutTestSupport {

	private final Book first = Book.of("Birinci Kitap", "987.65");

	private final Book second = Book.of("İkinci Kitap", "12.50");

	private final UUID paymentId = UUID.randomUUID();

	private UUID cartId;

	private void stubTwoBooks() {
		this.cartId = stubCart(new Line(this.first, 2), new Line(this.second, 1));
		stubLookup(this.first, this.second);
		stubReserveHeld();
		stubPaymentInitiated(this.paymentId, "initiated");
	}

	@Test
	void createsPendingOrderWithHeldStockAndAttachedPayment() throws Exception {
		stubTwoBooks();

		ResultActions result = checkout().andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("pending"))
			.andExpect(jsonPath("$.failureCode").isEmpty())
			.andExpect(jsonPath("$.currency").value("TRY"))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].bookId").value(this.first.id().toString()))
			.andExpect(jsonPath("$.items[0].title").value("Birinci Kitap"))
			.andExpect(jsonPath("$.items[0].quantity").value(2))
			.andExpect(jsonPath("$.items[1].bookId").value(this.second.id().toString()))
			.andExpect(jsonPath("$.items[1].title").value("İkinci Kitap"))
			.andExpect(jsonPath("$.items[1].quantity").value(1))
			.andExpect(jsonPath("$.address.recipientName").value(RECIPIENT))
			.andExpect(jsonPath("$.address.phone").value(PHONE))
			.andExpect(jsonPath("$.address.line1").value(LINE1))
			.andExpect(jsonPath("$.address.line2").value(LINE2))
			.andExpect(jsonPath("$.address.district").value(DISTRICT))
			.andExpect(jsonPath("$.address.city").value(CITY))
			.andExpect(jsonPath("$.address.postalCode").value(POSTAL_CODE))
			.andExpect(jsonPath("$.address.country").value("TR"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.updatedAt").isNotEmpty())
			.andExpect(jsonPath("$.stockState").doesNotExist())
			.andExpect(jsonPath("$.paymentId").doesNotExist())
			.andExpect(jsonPath("$.userId").doesNotExist())
			.andExpect(jsonPath("$.cartId").doesNotExist());
		UUID orderId = idOf(result);
		result.andExpect(header().string(HttpHeaders.LOCATION, "/api/orders/" + orderId));

		// Para biçimi Cart API'siyle aynı: JSON sayı, 2 ondalık.
		String body = result.andReturn().getResponse().getContentAsString();
		assertThat(body).contains("\"subtotal\":1987.80", "\"discountAmount\":0.00", "\"totalAmount\":1987.80",
				"\"unitPrice\":987.65", "\"lineTotal\":1975.30", "\"unitPrice\":12.50", "\"lineTotal\":12.50")
			.doesNotContain(this.paymentId.toString())
			.doesNotContain(this.userId.toString())
			.doesNotContain(this.cartId.toString());

		Map<String, Object> row = orderRow(orderId);
		assertThat(row).containsEntry("u", this.userId.toString())
			.containsEntry("c", this.cartId.toString())
			.containsEntry("s", "pending")
			.containsEntry("st", "held")
			.containsEntry("p", this.paymentId.toString())
			.containsEntry("cur", "TRY");
		assertThat(row.get("f")).isNull();
		assertThat((BigDecimal) row.get("sub")).isEqualByComparingTo("1987.80");
		assertThat((BigDecimal) row.get("disc")).isEqualByComparingTo("0.00");
		assertThat((BigDecimal) row.get("tot")).isEqualByComparingTo("1987.80");

		List<Map<String, Object>> history = historyRows(orderId);
		assertThat(history).hasSize(1);
		assertThat(history.get(0).get("fs")).isNull();
		assertThat(history.get(0)).containsEntry("ts", "pending").containsEntry("r", "ORDER_PLACED");

		List<Map<String, Object>> items = itemRows(orderId);
		assertThat(items).hasSize(2);
		assertItem(items.get(0), this.first, 2, "987.65", "1975.30");
		assertItem(items.get(1), this.second, 1, "12.50", "12.50");
	}

	@Test
	void reserveAndPaymentRequestsCarryTheNewOrderAndNoUserToken() throws Exception {
		stubTwoBooks();

		UUID orderId = idOf(checkout().andExpect(status().isCreated()));

		List<LoggedRequest> reserves = reserveRequests();
		assertThat(reserves).hasSize(1);
		String reserveBody = reserves.get(0).getBodyAsString();
		assertThat((String) JsonPath.read(reserveBody, "$.orderId")).isEqualTo(orderId.toString());
		assertThat((List<String>) JsonPath.read(reserveBody, "$.items[*].bookId"))
			.containsExactly(this.first.id().toString(), this.second.id().toString());
		assertThat((List<Integer>) JsonPath.read(reserveBody, "$.items[*].quantity")).containsExactly(2, 1);

		List<LoggedRequest> payments = paymentRequests();
		assertThat(payments).hasSize(1);
		String paymentBody = payments.get(0).getBodyAsString();
		assertThat((String) JsonPath.read(paymentBody, "$.orderId")).isEqualTo(orderId.toString());
		assertThat((String) JsonPath.read(paymentBody, "$.userId")).isEqualTo(this.userId.toString());
		assertThat((String) JsonPath.read(paymentBody, "$.currency")).isEqualTo("TRY");
		// Payment sözleşmesi: amount JSON sayı, en fazla 2 ondalık (metin değil).
		assertThat(paymentBody).contains("\"amount\":1987.80");

		List<LoggedRequest> all = allDownstreamRequests();
		assertThat(all).hasSize(4);
		assertThat(all).noneMatch(request -> request.containsHeader(HttpHeaders.AUTHORIZATION));
		assertThat(all).noneMatch(request -> request.getBodyAsString().contains(this.token));
		// Lookup public uç: anahtar yalnızca /internal/ isteklerinde.
		assertThat(all).allMatch(request -> request.getUrl().startsWith("/internal/")
				== request.containsHeader(InternalApiKey.HEADER));
	}

	/** Payment tekrar isteğinde ödeme sonuçlanmış dönebilir; checkout bunu yok sayar (sonuç olaydan gelir, Adım 6). */
	@Test
	void initiatedPaymentStateIsIgnored() throws Exception {
		stubTwoBooks();
		stubPaymentInitiated(this.paymentId, "succeeded");

		UUID orderId = idOf(checkout().andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("pending")));

		assertThat(orderRow(orderId)).containsEntry("s", "pending")
			.containsEntry("st", "held")
			.containsEntry("p", this.paymentId.toString());
		assertThat(historyRows(orderId)).hasSize(1);
	}

	/** Opsiyonel adres alanları gönderilmeyebilir; boş metin null saklanır (user-service ile aynı). */
	@Test
	void optionalAddressFieldsMayBeMissingOrBlank() throws Exception {
		stubHappyPath();
		String body = """
				{"address":{"recipientName":"Ali Veli","phone":"5551112233","line1":"Cad. 1","line2":"  ",\
				"district":"","city":"Ankara","country":"TR"}}""";

		UUID orderId = idOf(checkout(this.token, body).andExpect(status().isCreated())
			.andExpect(jsonPath("$.address.line2").isEmpty())
			.andExpect(jsonPath("$.address.district").isEmpty())
			.andExpect(jsonPath("$.address.postalCode").isEmpty()));

		assertThat(jdbc.queryForObject("""
				SELECT JSON_TYPE(JSON_EXTRACT(address_snapshot, '$.line2')) FROM orders WHERE id = UUID_TO_BIN(?)""",
				String.class, orderId.toString())).isEqualTo("NULL");
	}

	/** Diğer servislerle aynı Jackson politikası: bilinmeyen alanlar yok sayılır (gövdede ve adreste). */
	@Test
	void unknownFieldsAreIgnored() throws Exception {
		stubHappyPath();
		String body = """
				{"address":%s,"userId":"%s","items":[{"bookId":"%s","quantity":99}],"totalAmount":0.01}"""
			.formatted(ADDRESS_JSON.replace("\"country\":\"TR\"", "\"country\":\"TR\",\"label\":\"Ev\""),
					UUID.randomUUID(), UUID.randomUUID());

		UUID orderId = idOf(checkout(this.token, body).andExpect(status().isCreated())
			.andExpect(jsonPath("$.totalAmount").value(149.90))
			.andExpect(jsonPath("$.items[0].quantity").value(1)));

		assertThat(orderRow(orderId)).containsEntry("u", this.userId.toString());
	}

	private static void assertItem(Map<String, Object> item, Book book, int quantity, String unitPrice,
			String lineTotal) {
		assertThat(item).containsEntry("b", book.id().toString())
			.containsEntry("t", book.title())
			.containsEntry("q", quantity);
		assertThat((BigDecimal) item.get("up")).isEqualByComparingTo(unitPrice);
		assertThat((BigDecimal) item.get("lt")).isEqualByComparingTo(lineTotal);
	}

}
