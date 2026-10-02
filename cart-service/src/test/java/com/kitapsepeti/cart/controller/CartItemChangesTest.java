package com.kitapsepeti.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.entity.Cart;
import com.kitapsepeti.cart.support.CatalogStub.Response;
import com.kitapsepeti.cart.support.FakeCatalog;
import com.kitapsepeti.cart.support.FakeCatalog.Book;
import com.kitapsepeti.cart.support.SqlCapture;
import com.kitapsepeti.cart.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** {@code PATCH /api/cart/items/{bookId}}, {@code DELETE /api/cart/items/{bookId}} ve {@code DELETE /api/cart/items}. */
@ExtendWith(OutputCaptureExtension.class)
class CartItemChangesTest extends ApiTestSupport {

	private static final Instant T1 = Instant.parse("2026-03-01T10:15:30.123456Z");

	private static final String T1_DB = "2026-03-01 10:15:30.123456";

	private static final String T2_DB = "2026-03-01 10:20:30.123456";

	private final FakeCatalog catalog = new FakeCatalog();

	private final UUID userId = UUID.fromString(SUBJECT);

	private String token;

	@BeforeEach
	void useFakeCatalogAndFixedClock() {
		CATALOG.respondWith(catalog);
		token = TestJwt.user(SUBJECT);
		clock.fixAt(T1);
	}

	// --- PATCH ---

	@Test
	void patchSetsQuantityKeepsSnapshotAndStampsLineAndCartWithClock() throws Exception {
		Book book = catalog.publish("Adet", "10.00");
		add(book, 2);
		catalog.put(book.withPrice("12.00").withTitle("Yeni başlık"));
		clock.advance(Duration.ofMinutes(5));
		CATALOG.reset();
		CATALOG.respondWith(catalog);

		patchQuantity(book.id(), 5).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].quantity").value(5))
			.andExpect(jsonPath("$.items[0].title").value("Adet"))
			.andExpect(jsonPath("$.items[0].snapshotUnitPrice").value(10.00))
			.andExpect(jsonPath("$.items[0].currentUnitPrice").value(12.00))
			.andExpect(jsonPath("$.items[0].priceChanged").value(true))
			.andExpect(jsonPath("$.items[0].lineTotal").value(60.00));

		assertThat(jdbc.queryForObject("SELECT CAST(unit_price_snapshot AS CHAR) FROM cart_items", String.class))
			.isEqualTo("10.00");
		assertThat(dbTime("SELECT added_at FROM cart_items")).isEqualTo(T1_DB);
		assertThat(dbTime("SELECT updated_at FROM cart_items")).isEqualTo(T2_DB);
		assertThat(dbTime("SELECT updated_at FROM carts")).isEqualTo(T2_DB);
		assertThat(CATALOG.requests()).extracting(request -> request.path()).containsExactly("/api/books/lookup");
	}

	/** Karar: değişiklik yoksa damgalama da yok (UPDATE çalışmaz). */
	@Test
	void patchWithSameQuantityIsOkAndDoesNotStampOrUpdate() throws Exception {
		Book book = catalog.publish("Aynı", "10.00");
		add(book, 5);
		clock.advance(Duration.ofMinutes(5));

		SqlCapture.start();
		patchQuantity(book.id(), 5).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(5));
		List<String> statements = SqlCapture.stop();

		assertThat(statements).noneSatisfy(sql -> assertThat(sql.toLowerCase()).startsWith("update"));
		assertThat(dbTime("SELECT updated_at FROM cart_items")).isEqualTo(T1_DB);
		assertThat(dbTime("SELECT updated_at FROM carts")).isEqualTo(T1_DB);
	}

	@Test
	void patchAboveBusinessLimitIsConflictAndLeavesQuantity() throws Exception {
		Book book = catalog.publish("Limit", "10.00");
		add(book, 2);

		String body = patchQuantity(book.id(), 11).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CART_QUANTITY_LIMIT_EXCEEDED"))
			.andExpect(jsonPath("$.limit").value(10))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(book.id().toString()).doesNotContain("11");
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(2);
		patchQuantity(book.id(), 10).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(10));
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"quantity\":100}", "{\"quantity\":0}", "{\"quantity\":-1}", "{}", "{\"quantity\":null}" })
	void patchWithInvalidQuantityIsValidationError(String requestBody) throws Exception {
		Book book = catalog.publish("Geçersiz", "10.00");
		add(book, 2);

		String body = patchItem(book.id(), requestBody).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("quantity"))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(book.id().toString());
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(2);
	}

	@Test
	void patchWithoutCartOrForBookNotInCartIsNotFound() throws Exception {
		UUID missing = UUID.randomUUID();
		String noCart = patchQuantity(missing, 3).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
			.andExpect(jsonPath("$.instance").value("/api/cart/items/:bookId"))
			.andReturn().getResponse().getContentAsString();
		assertThat(noCart).doesNotContain(missing.toString());
		assertThat(cartCount()).isZero();

		add(catalog.publish("Sepette", "10.00"), 1);
		String notInCart = patchQuantity(missing, 3).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
			.andExpect(jsonPath("$.detail").value("Book is not in the cart."))
			.andReturn().getResponse().getContentAsString();
		assertThat(notInCart).doesNotContain(missing.toString());
	}

	@Test
	void quantityOfBookNoLongerForSaleCanStillBeChanged() throws Exception {
		Book book = catalog.publish("Kaldırılacak", "10.00");
		add(book, 1);
		catalog.unpublish(book.id());

		patchQuantity(book.id(), 4).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].quantity").value(4))
			.andExpect(jsonPath("$.items[0].available").value(false))
			.andExpect(jsonPath("$.subtotal").value(0.00));
	}

	@Test
	void patchWhileCatalogIsDownChangesQuantityAndServesSnapshot() throws Exception {
		Book book = catalog.publish("Kesinti", "10.00");
		add(book, 1);
		catalog.failWith(Response.problem(503));

		patchQuantity(book.id(), 3).andExpect(status().isOk())
			.andExpect(jsonPath("$.catalogStatus").value("UNAVAILABLE"))
			.andExpect(jsonPath("$.items[0].available").isEmpty())
			.andExpect(jsonPath("$.items[0].quantity").value(3))
			.andExpect(jsonPath("$.subtotal").value(30.00));
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(3);
	}

	// --- DELETE satır ---

	@Test
	void deleteRemovesLineReturnsRemainingAndStampsCart() throws Exception {
		Book first = catalog.publish("Silinecek", "10.00");
		Book second = catalog.publish("Kalacak", "20.00");
		add(first, 1);
		add(second, 2);
		clock.advance(Duration.ofMinutes(5));

		removeItem(first.id()).andExpect(status().isOk())
			.andExpect(jsonPath("$.lineCount").value(1))
			.andExpect(jsonPath("$.items[0].bookId").value(second.id().toString()))
			.andExpect(jsonPath("$.subtotal").value(40.00));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isEqualTo(1);
		assertThat(dbTime("SELECT updated_at FROM carts")).isEqualTo(T2_DB);
		assertThat(dbTime("SELECT updated_at FROM cart_items")).isEqualTo(T1_DB);
	}

	/** Karar: olmayan satırı silmek değişiklik değildir; damgalama yok. */
	@Test
	void deletingSameLineAgainIsOkAndChangesNothing() throws Exception {
		Book first = catalog.publish("Silinecek", "10.00");
		Book second = catalog.publish("Kalacak", "20.00");
		add(first, 1);
		add(second, 2);
		clock.advance(Duration.ofMinutes(5));
		removeItem(first.id()).andExpect(status().isOk());
		clock.advance(Duration.ofMinutes(5));

		SqlCapture.start();
		removeItem(first.id()).andExpect(status().isOk())
			.andExpect(jsonPath("$.lineCount").value(1))
			.andExpect(jsonPath("$.items[0].bookId").value(second.id().toString()));
		List<String> statements = SqlCapture.stop();

		assertThat(statements).noneSatisfy(sql -> assertThat(sql.toLowerCase()).matches("^(update|delete|insert).*"));
		assertThat(dbTime("SELECT updated_at FROM carts")).isEqualTo(T2_DB);
	}

	@Test
	void deleteWithoutCartReturnsEmptyCartWithoutCreatingOne() throws Exception {
		removeItem(UUID.randomUUID()).andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.catalogStatus").value("VERIFIED"));

		assertThat(cartCount()).isZero();
		assertThat(CATALOG.requests()).isEmpty();
	}

	@Test
	void removingLastLineKeepsCartActiveAndNextAddReusesIt() throws Exception {
		Book book = catalog.publish("Son satır", "10.00");
		add(book, 1);
		String cartId = cartId();

		removeItem(book.id()).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
		assertThat(jdbc.queryForObject("SELECT status FROM carts", String.class)).isEqualTo("active");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isZero();

		add(catalog.publish("Yeni", "10.00"), 1);
		assertThat(cartCount()).isEqualTo(1);
		assertThat(cartId()).isEqualTo(cartId);
	}

	// --- DELETE tümü ---

	@Test
	void clearRemovesAllLinesKeepsActiveCartAndDoesNotCallCatalog() throws Exception {
		for (int i = 0; i < 3; i++) {
			add(catalog.publish("Kitap " + i, "10.00"), 1);
		}
		String cartId = cartId();
		clock.advance(Duration.ofMinutes(5));
		CATALOG.reset();
		CATALOG.respondWith(catalog);

		clear().andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.lineCount").value(0))
			.andExpect(jsonPath("$.subtotal").value(0.00))
			.andExpect(jsonPath("$.catalogStatus").value("VERIFIED"));

		assertThat(CATALOG.requests()).isEmpty();
		assertThat(cartId()).isEqualTo(cartId);
		assertThat(jdbc.queryForObject("SELECT status FROM carts", String.class)).isEqualTo("active");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isZero();
		assertThat(dbTime("SELECT updated_at FROM carts")).isEqualTo(T2_DB);
	}

	@Test
	void clearingEmptyCartDoesNotStampIt() throws Exception {
		Book book = catalog.publish("Tek", "10.00");
		add(book, 1);
		clock.advance(Duration.ofMinutes(5));
		clear().andExpect(status().isOk());
		clock.advance(Duration.ofMinutes(5));

		clear().andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

		assertThat(dbTime("SELECT updated_at FROM carts")).isEqualTo(T2_DB);
	}

	@Test
	void clearWithoutCartReturnsEmptyCartWithoutCreatingOne() throws Exception {
		clear().andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

		assertThat(cartCount()).isZero();
		assertThat(CATALOG.requests()).isEmpty();
	}

	@Test
	void sameBookCanBeAddedAgainAfterClear() throws Exception {
		Book book = catalog.publish("Tekrar", "10.00");
		add(book, 3);
		String cartId = cartId();
		clear().andExpect(status().isOk());

		add(book, 1).andExpect(jsonPath("$.items[0].quantity").value(1));

		assertThat(cartId()).isEqualTo(cartId);
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(1);
	}

	// --- genel ---

	@Test
	void otherUsersCartCannotBeChanged() throws Exception {
		Book book = catalog.publish("Ortak kitap", "10.00");
		String otherToken = TestJwt.user(UUID.randomUUID().toString());
		mockMvc.perform(post("/api/cart/items").with(bearer(otherToken)).contentType(MediaType.APPLICATION_JSON)
				.content(addBody(book.id(), 4)))
			.andExpect(status().isOk());

		patchQuantity(book.id(), 1).andExpect(status().isNotFound());
		removeItem(book.id()).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
		clear().andExpect(status().isOk());

		assertThat(cartCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(4);
	}

	@Test
	void checkedOutCartIsNeverTouched() throws Exception {
		Book book = catalog.publish("Siparişte", "10.00");
		Cart historic = Cart.openFor(userId, clock);
		historic.addItem(book.id(), 2, new BigDecimal("10.00"), "TRY", book.title(), null, clock);
		historic.checkout(clock);
		carts.saveAndFlush(historic);
		clock.advance(Duration.ofMinutes(5));

		patchQuantity(book.id(), 5).andExpect(status().isNotFound());
		removeItem(book.id()).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
		clear().andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

		assertThat(cartCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT CONCAT(c.status, '|', i.quantity) FROM carts c "
				+ "JOIN cart_items i ON i.cart_id = c.id", String.class))
			.isEqualTo("checked_out|2");
		assertThat(dbTime("SELECT updated_at FROM carts")).isEqualTo(T1_DB);
	}

	@Test
	void requestsWithoutTokenAreUnauthorized() throws Exception {
		UUID bookId = UUID.randomUUID();
		String body = mockMvc.perform(patch("/api/cart/items/{bookId}", bookId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"quantity\":1}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.instance").value("/api/cart/items/:bookId"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(bookId.toString());
		mockMvc.perform(delete("/api/cart/items/{bookId}", bookId)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/cart/items")).andExpect(status().isUnauthorized());
	}

	@Test
	void invalidPathUuidAndBrokenJsonAreMalformedRequests() throws Exception {
		patchItem("kitap-degil", "{\"quantity\":1}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		mockMvc.perform(delete("/api/cart/items/{bookId}", "kitap-degil").with(bearer(token)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
			.andExpect(jsonPath("$.instance").value("/api/cart/items/:bookId"));
		patchItem(UUID.randomUUID(), "{\"quantity\":").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		assertThat(cartCount()).isZero();
	}

	@Test
	void logsContainNoBookIdUserIdOrToken(CapturedOutput output) throws Exception {
		Book book = catalog.publish("Log", "10.00");
		UUID missing = UUID.randomUUID();
		add(book, 2);
		patchQuantity(book.id(), 11).andExpect(status().isConflict());
		patchQuantity(missing, 1).andExpect(status().isNotFound());
		patchItem(book.id(), "{\"quantity\":0}").andExpect(status().isBadRequest());
		mockMvc.perform(delete("/api/cart/items/{bookId}", "kitap-degil").with(bearer(token)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(delete("/api/cart/items/{bookId}", missing)).andExpect(status().isUnauthorized());
		catalog.failWith(Response.problem(503));
		patchQuantity(book.id(), 3).andExpect(status().isOk());
		catalog.recover();
		removeItem(book.id()).andExpect(status().isOk());
		clear().andExpect(status().isOk());

		for (String secret : List.of(book.id().toString(), missing.toString(), "kitap-degil", SUBJECT, token)) {
			assertThat(output).doesNotContain(secret);
		}
		assertThat(output).contains("PATCH /api/cart/items/:bookId -> CART_QUANTITY_LIMIT_EXCEEDED")
			.contains("PATCH /api/cart/items/:bookId -> RESOURCE_NOT_FOUND")
			.contains("PATCH /api/cart/items/:bookId -> VALIDATION_FAILED")
			.contains("DELETE /api/cart/items/:bookId -> MALFORMED_REQUEST")
			.contains("DELETE /api/cart/items/:bookId -> UNAUTHORIZED")
			.contains("PATCH /api/cart/items/:bookId -> CATALOG_UNAVAILABLE")
			.doesNotContain(" ERROR ");
	}

	// --- yardımcılar ---

	private ResultActions add(Book book, int quantity) throws Exception {
		return mockMvc.perform(post("/api/cart/items").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
				.content(addBody(book.id(), quantity)))
			.andExpect(status().isOk());
	}

	private static String addBody(UUID bookId, int quantity) {
		return "{\"bookId\":\"" + bookId + "\",\"quantity\":" + quantity + "}";
	}

	private ResultActions patchQuantity(UUID bookId, int quantity) throws Exception {
		return patchItem(bookId, "{\"quantity\":" + quantity + "}");
	}

	private ResultActions patchItem(Object bookId, String body) throws Exception {
		return mockMvc.perform(patch("/api/cart/items/{bookId}", bookId).with(bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions removeItem(UUID bookId) throws Exception {
		return mockMvc.perform(delete("/api/cart/items/{bookId}", bookId).with(bearer(token)));
	}

	private ResultActions clear() throws Exception {
		return mockMvc.perform(delete("/api/cart/items").with(bearer(token)));
	}

	private int cartCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM carts", Integer.class);
	}

	private String cartId() {
		return jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM carts WHERE status = 'active'", String.class);
	}

	private String dbTime(String select) {
		String column = select.substring("SELECT ".length(), select.indexOf(" FROM"));
		return jdbc.queryForObject(select.replace(column, "DATE_FORMAT(" + column + ", '%Y-%m-%d %H:%i:%s.%f')"),
				String.class);
	}

}
