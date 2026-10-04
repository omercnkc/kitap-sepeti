package com.kitapsepeti.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.order.entity.AddressSnapshot;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderLine;
import com.kitapsepeti.order.service.OrderTransactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sipariş yazılmadan önceki hatalar: doğru HTTP + code, kullanıcının yeni sipariş satırı yok, rezervasyon ve ödeme
 * çağrılmadı. Hangi kitabın sorunlu olduğu yanıtta yok.
 */
class CheckoutBeforeOrderFailuresTest extends CheckoutTestSupport {

	@Autowired
	private OrderTransactions transactions;

	@AfterEach
	void noOrderReservationOrPayment() {
		assertThat(reserveRequests()).isEmpty();
		assertThat(paymentRequests()).isEmpty();
	}

	@Test
	void pendingOrderExistsReturnsItsIdWithoutCallingCart() throws Exception {
		UUID pendingId = this.transactions.insert(Order.place(this.userId, UUID.randomUUID(), "TRY",
				List.of(new OrderLine(UUID.randomUUID(), "Bekleyen", 1, new BigDecimal("10.00"))),
				new AddressSnapshot("A", "1", "B", null, null, "C", null, "TR"), this.clock)).id();
		stubHappyPath();

		checkout().andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("ORDER_PENDING_EXISTS"))
			.andExpect(jsonPath("$.orderId").value(pendingId.toString()))
			.andExpect(jsonPath("$.instance").value(CHECKOUT));

		assertThat(cartRequests()).isEmpty();
		assertThat(orderCount()).isEqualTo(1);
	}

	@Test
	void missingCartIsCartEmpty() throws Exception {
		stubCart(json(200, "{\"cartId\":null,\"updatedAt\":null,\"items\":[]}"));

		expectProblem(422, "CART_EMPTY");
	}

	@Test
	void activeCartWithoutItemsIsCartEmpty() throws Exception {
		stubCart();

		expectProblem(422, "CART_EMPTY");
	}

	@Test
	void cartFailureIsCartUnavailable() throws Exception {
		stubCart(problem(500, "INTERNAL_ERROR"));

		expectProblem(503, "CART_UNAVAILABLE");
	}

	@Test
	void cartDownIsCartUnavailable() throws Exception {
		CART.stop();

		expectProblem(503, "CART_UNAVAILABLE");
	}

	@Test
	void lookupFailureIsCatalogUnavailable() throws Exception {
		stubCart(new Line(Book.of("Kitap", "10.00"), 1));
		stubLookup(problem(503, "SERVICE_UNAVAILABLE"));

		expectProblem(503, "CATALOG_UNAVAILABLE");
	}

	@Test
	void bookOutOfStockIsBookNotAvailableWithoutIds() throws Exception {
		Book available = Book.of("Var", "10.00");
		Book soldOut = Book.of("Tükendi", "10.00").outOfStock();
		stubCart(new Line(available, 1), new Line(soldOut, 1));
		stubLookup(available, soldOut);

		String body = expectProblem(409, "BOOK_NOT_AVAILABLE");

		assertThat(body).doesNotContain(soldOut.id().toString()).doesNotContain(available.id().toString());
	}

	@Test
	void bookMissingFromCatalogIsBookNotAvailable() throws Exception {
		Book available = Book.of("Var", "10.00");
		Book missing = Book.of("Yok", "10.00");
		stubCart(new Line(available, 1), new Line(missing, 1));
		stubLookup(available);

		String body = expectProblem(409, "BOOK_NOT_AVAILABLE");

		assertThat(body).doesNotContain(missing.id().toString());
	}

	@Test
	void differentCurrenciesAreMixedCurrency() throws Exception {
		Book lira = Book.of("Lira", "10.00");
		Book euro = Book.of("Avro", "10.00").withCurrency("EUR");
		stubCart(new Line(lira, 1), new Line(euro, 1));
		stubLookup(lira, euro);

		expectProblem(422, "MIXED_CURRENCY");
	}

	@Test
	void allFreeCartIsOrderTotalZero() throws Exception {
		Book free = Book.of("Ücretsiz", "0.00");
		stubCart(new Line(free, 3));
		stubLookup(free);

		expectProblem(422, "ORDER_TOTAL_ZERO");
	}

	@Test
	void totalBeyondDecimalRangeIsOrderTotalTooLarge() throws Exception {
		Book expensive = Book.of("Pahalı", "9999999999.99");
		stubCart(new Line(expensive, 2));
		stubLookup(expensive);

		expectProblem(422, "ORDER_TOTAL_TOO_LARGE");
	}

	/** Diğer {@code Order.place} kuralları 500 değil, kendi koduyla 422. */
	@Test
	void priceWithMoreThanTwoDecimalsIsInvalidPrice() throws Exception {
		Book odd = Book.of("Küsuratlı", "10.005");
		stubCart(new Line(odd, 1));
		stubLookup(odd);

		expectProblem(422, "INVALID_PRICE");
	}

	@Test
	void malformedCurrencyIsInvalidCurrency() throws Exception {
		Book lower = Book.of("Küçük harf", "10.00").withCurrency("try");
		stubCart(new Line(lower, 1));
		stubLookup(lower);

		expectProblem(422, "INVALID_CURRENCY");
	}

	// --- Doğrulama: Cart'a da gidilmez, gönderilen değer yanıtta yok ---

	@Test
	void missingAddressIsValidationFailed() throws Exception {
		validationFailed("{}", "address");
		validationFailed("{\"address\":null}", "address");
	}

	@ParameterizedTest
	@ValueSource(strings = { "recipientName", "phone", "line1", "city", "country" })
	void missingRequiredFieldIsValidationFailed(String field) throws Exception {
		String address = ADDRESS_JSON.replaceAll("\"" + field + "\":\"[^\"]*\",?", "").replace(",}", "}");
		validationFailed("{\"address\":" + address + "}", "address." + field);
	}

	@ParameterizedTest
	@ValueSource(strings = { "recipientName", "phone", "line1", "city" })
	void blankRequiredFieldIsValidationFailed(String field) throws Exception {
		validationFailed(withField(field, "   "), "address." + field);
	}

	@Test
	void tooLongFieldsAreValidationFailedWithoutEchoingTheValue() throws Exception {
		String[][] cases = { { "recipientName", "121" }, { "phone", "33" }, { "line1", "201" }, { "line2", "201" },
				{ "district", "81" }, { "city", "81" }, { "postalCode", "17" } };
		for (String[] tooLong : cases) {
			String value = "Ş".repeat(Integer.parseInt(tooLong[1]) - 1) + "Q";
			String body = validationFailed(withField(tooLong[0], value), "address." + tooLong[0]);
			assertThat(body).doesNotContain(value).doesNotContain("ŞŞŞ");
		}
	}

	@Test
	void fieldsAtMaximumLengthAreAccepted() throws Exception {
		stubHappyPath();
		String body = """
				{"address":{"recipientName":"%s","phone":"%s","line1":"%s","line2":"%s","district":"%s","city":"%s",\
				"postalCode":"%s","country":"TR"}}""".formatted("ş".repeat(120), "5".repeat(32), "a".repeat(200),
				"b".repeat(200), "c".repeat(80), "d".repeat(80), "e".repeat(16));

		checkout(this.token, body).andExpect(status().isCreated());
		// Bu test sipariş yazar; @AfterEach kontrolünden önce kayıtları temizle.
		CATALOG.server().resetRequests();
		PAYMENT.server().resetRequests();
	}

	@ParameterizedTest
	@ValueSource(strings = { "tr", "TUR", "T", "", "T1", "XYZW" })
	void countryMustBeTwoUpperCaseLetters(String country) throws Exception {
		String body = validationFailed(withField("country", country), "address.country");
		if (country.length() > 1) {
			assertThat(body).doesNotContain("\"" + country + "\"");
		}
	}

	@Test
	void brokenJsonIsMalformedRequest() throws Exception {
		checkout(this.token, "{\"address\":").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		checkout(this.token, "{\"address\":\"metin\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		assertThat(cartRequests()).isEmpty();
		assertThat(orderCount()).isZero();
	}

	@Test
	void checkoutWithoutTokenIsUnauthorized() throws Exception {
		stubHappyPath();
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(CHECKOUT)
			.contentType(MediaType.APPLICATION_JSON)
			.content(CHECKOUT_BODY)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		assertThat(cartRequests()).isEmpty();
	}

	private String expectProblem(int httpStatus, String code) throws Exception {
		ResultActions result = checkout().andExpect(status().is(httpStatus))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.instance").value(CHECKOUT))
			.andExpect(jsonPath("$.orderId").doesNotExist());
		assertThat(orderCount()).isZero();
		return result.andReturn().getResponse().getContentAsString();
	}

	private String validationFailed(String body, String field) throws Exception {
		stubHappyPath();
		String response = checkout(this.token, body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItem(field)))
			.andExpect(jsonPath("$.errors[*].rejectedValue").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(cartRequests()).isEmpty();
		assertThat(orderCount()).isZero();
		return response;
	}

	private static String withField(String field, String value) {
		String address = ADDRESS_JSON.replaceAll("\"" + field + "\":\"[^\"]*\"", "\"" + field + "\":\"" + value + "\"");
		return "{\"address\":" + address + "}";
	}

}
