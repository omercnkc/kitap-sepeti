package com.kitapsepeti.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.dto.response.CartLineResponse;
import com.kitapsepeti.cart.dto.response.CartResponse;
import com.kitapsepeti.cart.dto.response.CatalogStatus;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** {@code GET /api/cart} ve {@code POST /api/cart/items}; Catalog {@link FakeCatalog} ile taklit edilir. */
@ExtendWith(OutputCaptureExtension.class)
class CartControllerTest extends ApiTestSupport {

	private static final Instant T1 = Instant.parse("2026-03-01T10:15:30.123456Z");

	private static final Set<String> CART_FIELDS = Set.of("items", "lineCount", "itemCount", "subtotal", "currency",
			"catalogStatus");

	private static final Set<String> LINE_FIELDS = Set.of("bookId", "title", "coverUrl", "quantity", "currency",
			"snapshotUnitPrice", "currentUnitPrice", "available", "priceChanged", "lineTotal");

	private final FakeCatalog catalog = new FakeCatalog();

	private final UUID userId = UUID.fromString(SUBJECT);

	private String token;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void useFakeCatalog() {
		CATALOG.respondWith(catalog);
		token = TestJwt.user(SUBJECT);
	}

	// --- GET /api/cart ---

	@Test
	void getWithoutActiveCartReturnsEmptyCartWithoutCreatingOneOrCallingCatalog() throws Exception {
		String json = getCart().andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.lineCount").value(0))
			.andExpect(jsonPath("$.itemCount").value(0))
			.andExpect(jsonPath("$.currency").value(nullValue()))
			.andExpect(jsonPath("$.catalogStatus").value("VERIFIED"))
			.andReturn().getResponse().getContentAsString();

		assertThat(json).contains("\"subtotal\":0.00");
		assertThat(fieldsOf(json)).isEqualTo(CART_FIELDS);
		assertThat(cartCount()).isZero();
		assertThat(CATALOG.requests()).isEmpty();
	}

	@Test
	void getVerifiesLinesAgainstCatalogInAddedOrderAndTotalsUseCurrentPrices() throws Exception {
		Book first = catalog.publish("Kürk Mantolu Madonna", "149.90");
		Book second = catalog.publish("İnce Memed", "50.00");
		add(first, 2).andExpect(status().isOk());
		clock.advance(Duration.ofSeconds(1));
		add(second, 1).andExpect(status().isOk());
		catalog.put(first.withPrice("139.90"));
		CATALOG.reset();
		CATALOG.respondWith(catalog);

		String json = getCart().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		CartResponse cart = read(json);

		assertThat(cart.catalogStatus()).isEqualTo(CatalogStatus.VERIFIED);
		assertThat(cart.items()).extracting(CartLineResponse::bookId).containsExactly(first.id(), second.id());
		CartLineResponse changed = cart.items().get(0);
		assertThat(changed.title()).isEqualTo("Kürk Mantolu Madonna");
		assertThat(changed.quantity()).isEqualTo(2);
		assertThat(changed.currency()).isEqualTo("TRY");
		assertThat(changed.snapshotUnitPrice()).isEqualTo(new BigDecimal("149.90"));
		assertThat(changed.currentUnitPrice()).isEqualTo(new BigDecimal("139.90"));
		assertThat(changed.available()).isTrue();
		assertThat(changed.priceChanged()).isTrue();
		assertThat(changed.lineTotal()).isEqualTo(new BigDecimal("279.80"));
		CartLineResponse same = cart.items().get(1);
		assertThat(same.currentUnitPrice()).isEqualTo(new BigDecimal("50.00"));
		assertThat(same.priceChanged()).isFalse();
		assertThat(same.lineTotal()).isEqualTo(new BigDecimal("50.00"));
		assertThat(cart.lineCount()).isEqualTo(2);
		assertThat(cart.itemCount()).isEqualTo(3);
		assertThat(cart.subtotal()).isEqualTo(new BigDecimal("329.80"));
		assertThat(cart.currency()).isEqualTo("TRY");
		assertThat(json).contains("\"snapshotUnitPrice\":149.90", "\"currentUnitPrice\":139.90",
				"\"lineTotal\":50.00", "\"subtotal\":329.80");
		assertNoIdentifiers(json);
	}

	@Test
	void missingAndOutOfStockLinesAreUnavailableAndExcludedFromSubtotal() throws Exception {
		Book kept = catalog.publish("Kalan", "20.00");
		Book drafted = catalog.publish("Taslağa çekilen", "30.00");
		Book soldOut = catalog.publish("Tükenen", "40.00");
		add(kept, 1).andExpect(status().isOk());
		add(drafted, 2).andExpect(status().isOk());
		add(soldOut, 3).andExpect(status().isOk());
		catalog.unpublish(drafted.id());
		catalog.put(soldOut.outOfStock().withPrice("45.00"));

		CartResponse cart = read(getCart().andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

		assertThat(cart.catalogStatus()).isEqualTo(CatalogStatus.VERIFIED);
		assertThat(line(cart, kept).available()).isTrue();
		for (Book gone : List.of(drafted, soldOut)) {
			CartLineResponse line = line(cart, gone);
			assertThat(line.available()).isFalse();
			assertThat(line.currentUnitPrice()).isNull();
			assertThat(line.priceChanged()).isFalse();
		}
		assertThat(line(cart, drafted).lineTotal()).isEqualTo(new BigDecimal("60.00"));
		assertThat(line(cart, soldOut).lineTotal()).isEqualTo(new BigDecimal("120.00"));
		assertThat(cart.itemCount()).isEqualTo(6);
		assertThat(cart.subtotal()).isEqualTo(new BigDecimal("20.00"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "503", "timeout" })
	void catalogOutageServesSnapshotPricesWithSingleWarn(String failure, CapturedOutput output) throws Exception {
		Book first = catalog.publish("Bir", "149.90");
		Book second = catalog.publish("İki", "10.00");
		add(first, 2).andExpect(status().isOk());
		add(second, 1).andExpect(status().isOk());
		catalog.failWith("503".equals(failure) ? Response.problem(503)
				: Response.json(200, "{\"items\":[]}").delayed(Duration.ofMillis(3500)));

		String json = getCart().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		CartResponse cart = read(json);

		assertThat(cart.catalogStatus()).isEqualTo(CatalogStatus.UNAVAILABLE);
		assertThat(cart.items()).allSatisfy(line -> {
			assertThat(line.available()).isNull();
			assertThat(line.currentUnitPrice()).isNull();
			assertThat(line.priceChanged()).isFalse();
		});
		assertThat(json).contains("\"available\":null", "\"currentUnitPrice\":null");
		assertThat(cart.subtotal()).isEqualTo(new BigDecimal("309.80"));
		assertThat(cart.currency()).isEqualTo("TRY");
		String cause = "503".equals(failure) ? "ServiceUnavailable" : "SocketTimeoutException";
		assertThat(output.getOut().lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR ")))
			.singleElement()
			.satisfies(line -> assertThat(line)
				.endsWith("GET /api/cart -> CATALOG_UNAVAILABLE (cause=" + cause + ", served from snapshot)"));
		assertThat(output).doesNotContain(CATALOG.baseUrl()).doesNotContain(first.id().toString());
	}

	@Test
	void otherUsersCartIsNotVisible() throws Exception {
		Book book = catalog.publish("Başkasının", "10.00");
		String otherToken = TestJwt.user(UUID.randomUUID().toString());
		mockMvc.perform(post("/api/cart/items").with(bearer(otherToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(book.id(), 1)))
			.andExpect(status().isOk());

		getCart().andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
		assertThat(cartCount()).isEqualTo(1);
	}

	@Test
	void getRunsSingleSqlStatementAndSingleLookupRequest() throws Exception {
		for (int i = 0; i < 3; i++) {
			add(catalog.publish("Kitap " + i, "10.00"), 1).andExpect(status().isOk());
		}
		CATALOG.reset();
		CATALOG.respondWith(catalog);

		SqlCapture.start();
		getCart().andExpect(status().isOk()).andExpect(jsonPath("$.lineCount").value(3));
		List<String> statements = SqlCapture.stop();

		assertThat(statements).singleElement().satisfies(sql -> assertThat(sql.toLowerCase()).contains("join"));
		assertThat(CATALOG.requests()).singleElement().satisfies(request -> {
			assertThat(request.path()).isEqualTo("/api/books/lookup");
			assertThat(request.query().split("&")).hasSize(3);
		});
	}

	// --- POST /api/cart/items ---

	@Test
	void firstAddOpensCartWithSnapshotFromCatalog() throws Exception {
		Book book = catalog.put(new Book(UUID.randomUUID(), "Kürk Mantolu Madonna", "149.9", "TRY",
				"https://cdn.example.com/k.jpg", true));

		String json = add(book, 2).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		CartResponse cart = read(json);

		assertThat(cart.items()).singleElement().satisfies(line -> {
			assertThat(line.bookId()).isEqualTo(book.id());
			assertThat(line.title()).isEqualTo("Kürk Mantolu Madonna");
			assertThat(line.coverUrl()).isEqualTo("https://cdn.example.com/k.jpg");
			assertThat(line.quantity()).isEqualTo(2);
			assertThat(line.snapshotUnitPrice()).isEqualTo(new BigDecimal("149.90"));
			assertThat(line.currentUnitPrice()).isEqualTo(new BigDecimal("149.90"));
			assertThat(line.available()).isTrue();
			assertThat(line.lineTotal()).isEqualTo(new BigDecimal("299.80"));
		});
		assertThat(fieldsOf(json)).isEqualTo(CART_FIELDS);
		assertNoIdentifiers(json);
		assertThat(jdbc.queryForObject("SELECT CONCAT(c.status, '|', BIN_TO_UUID(c.user_id), '|', BIN_TO_UUID(i.book_id), "
				+ "'|', i.quantity, '|', CAST(i.unit_price_snapshot AS CHAR), '|', i.currency_snapshot, '|', "
				+ "i.title_snapshot, '|', i.cover_url_snapshot) FROM carts c JOIN cart_items i ON i.cart_id = c.id",
				String.class))
			.isEqualTo("active|" + userId + "|" + book.id() + "|2|149.90|TRY|Kürk Mantolu Madonna|"
					+ "https://cdn.example.com/k.jpg");
		assertThat(CATALOG.requests()).extracting(request -> request.path())
			.containsExactly("/api/books/" + book.id(), "/api/books/lookup");
	}

	@Test
	void quantityDefaultsToOne() throws Exception {
		Book book = catalog.publish("Tek", "10.00");

		mockMvc.perform(post("/api/cart/items").with(bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + book.id() + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].quantity").value(1));
	}

	/** Flush tuzağına karşı: tekrar ekleme aynı satırı UPDATE eder; DELETE + INSERT yok. */
	@Test
	void reAddingSameBookIncreasesQuantityRefreshesSnapshotAndUpdatesRowInPlace() throws Exception {
		Book book = catalog.publish("Eski başlık", "149.90");
		add(book, 2).andExpect(status().isOk());
		String rowId = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM cart_items", String.class);
		catalog.put(book.withPrice("139.90").withTitle("Yeni başlık"));

		SqlCapture.start();
		String json = add(book, 3).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> statements = SqlCapture.stop();

		CartResponse cart = read(json);
		assertThat(cart.items()).singleElement().satisfies(line -> {
			assertThat(line.quantity()).isEqualTo(5);
			assertThat(line.title()).isEqualTo("Yeni başlık");
			assertThat(line.snapshotUnitPrice()).isEqualTo(new BigDecimal("139.90"));
			assertThat(line.priceChanged()).isFalse();
		});
		assertThat(jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM cart_items", String.class)).containsExactly(rowId);
		assertThat(statements).noneSatisfy(sql -> assertThat(sql.toLowerCase()).startsWith("delete"))
			.noneSatisfy(sql -> assertThat(sql.toLowerCase()).startsWith("insert"))
			.anySatisfy(sql -> assertThat(sql.toLowerCase()).startsWith("update cart_items"));
	}

	@Test
	void quantityAboveBusinessLimitIsConflictAndLeavesLineUnchanged() throws Exception {
		Book book = catalog.publish("Limit", "10.00");
		add(book, 8).andExpect(status().isOk());

		String body = add(book, 3).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CART_QUANTITY_LIMIT_EXCEEDED"))
			.andExpect(jsonPath("$.limit").value(10))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(book.id().toString()).doesNotContain("bookId").doesNotContain("quantity\"");
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(8);

		Book other = catalog.publish("Tek seferde", "10.00");
		add(other, 11).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CART_QUANTITY_LIMIT_EXCEEDED"))
			.andExpect(jsonPath("$.limit").value(10));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isEqualTo(1);

		add(book, 2).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(10));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 100 })
	void quantityOutsideOneToNinetyNineIsValidationErrorWithoutCallingCatalog(int quantity) throws Exception {
		UUID bookId = catalog.publish("Geçersiz adet", "10.00").id();

		String body = add(bookId, quantity).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("quantity"))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(bookId.toString());
		assertThat(CATALOG.requests()).isEmpty();
		assertThat(cartCount()).isZero();
	}

	@Test
	void lineLimitRejectsNewBookButExistingLineCanStillGrow() throws Exception {
		Book existing = catalog.publish("Sepetteki", "10.00");
		Cart cart = Cart.openFor(userId, clock);
		cart.addItem(existing.id(), 1, new BigDecimal("10.00"), "TRY", existing.title(), null, clock);
		for (int i = 1; i < 50; i++) {
			cart.addItem(UUID.randomUUID(), 1, new BigDecimal("5.00"), "TRY", "Dolgu " + i, null, clock);
		}
		carts.saveAndFlush(cart);
		Book fresh = catalog.publish("Yeni", "10.00");

		String body = add(fresh, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CART_LINE_LIMIT_EXCEEDED"))
			.andExpect(jsonPath("$.limit").value(50))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(fresh.id().toString());

		add(existing, 2).andExpect(status().isOk())
			.andExpect(jsonPath("$.lineCount").value(50))
			.andExpect(jsonPath("$.catalogStatus").value("VERIFIED"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isEqualTo(50);
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE book_id = UUID_TO_BIN(?)", Integer.class,
				existing.id().toString()))
			.isEqualTo(3);
	}

	@ParameterizedTest
	@ValueSource(strings = { "unpublished", "outOfStock" })
	void unavailableBookIsConflictAndNoCartIsOpened(String kind) throws Exception {
		Book book = catalog.publish("Satışta değil", "10.00");
		if ("unpublished".equals(kind)) {
			catalog.unpublish(book.id());
		}
		else {
			catalog.put(book.outOfStock());
		}

		String body = add(book, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BOOK_NOT_AVAILABLE"))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(book.id().toString());
		assertThat(cartCount()).isZero();
	}

	@Test
	void catalogOutageOnAddIsServiceUnavailableAndCartIsUntouched() throws Exception {
		clock.fixAt(T1);
		Book book = catalog.publish("Var olan", "10.00");
		add(book, 1).andExpect(status().isOk());
		clock.advance(Duration.ofMinutes(1));
		catalog.failWith(Response.problem(503));

		add(catalog.publish("Yeni", "10.00"), 1).andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
		add(book, 1).andExpect(status().isServiceUnavailable());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(1);
		assertThat(dbTime("SELECT updated_at FROM carts")).isEqualTo("2026-03-01 10:15:30.123456");
	}

	@Test
	void catalogOutageOnFirstAddOpensNoCart() throws Exception {
		catalog.failWith(Response.problem(503));

		add(UUID.randomUUID(), 1).andExpect(status().isServiceUnavailable());

		assertThat(cartCount()).isZero();
	}

	@Test
	void addingAfterCheckoutOpensNewActiveCartAndLeavesOldOneUnchanged() throws Exception {
		clock.fixAt(T1);
		Book old = catalog.publish("Eski siparişte", "10.00");
		Cart historic = Cart.openFor(userId, clock);
		historic.addItem(old.id(), 4, new BigDecimal("10.00"), "TRY", old.title(), null, clock);
		historic.checkout(clock);
		carts.saveAndFlush(historic);
		clock.advance(Duration.ofHours(1));

		add(old, 1).andExpect(status().isOk())
			.andExpect(jsonPath("$.lineCount").value(1))
			.andExpect(jsonPath("$.items[0].quantity").value(1));

		assertThat(jdbc.queryForList("SELECT CONCAT(c.status, '|', i.quantity, '|', "
				+ "DATE_FORMAT(c.updated_at, '%Y-%m-%d %H:%i:%s.%f')) FROM carts c JOIN cart_items i ON i.cart_id = c.id "
				+ "ORDER BY c.created_at", String.class))
			.containsExactly("checked_out|4|2026-03-01 10:15:30.123456", "active|1|2026-03-01 11:15:30.123456");
	}

	@Test
	void timestampsComeFromClockAndSecondAddAdvancesThem() throws Exception {
		clock.fixAt(T1);
		Book book = catalog.publish("Saatli", "10.00");
		add(book, 1).andExpect(status().isOk());

		assertThat(dbTime("SELECT created_at FROM carts")).isEqualTo("2026-03-01 10:15:30.123456");
		assertThat(dbTime("SELECT updated_at FROM carts")).isEqualTo("2026-03-01 10:15:30.123456");
		assertThat(dbTime("SELECT added_at FROM cart_items")).isEqualTo("2026-03-01 10:15:30.123456");
		assertThat(dbTime("SELECT updated_at FROM cart_items")).isEqualTo("2026-03-01 10:15:30.123456");

		clock.advance(Duration.ofMinutes(5));
		add(book, 1).andExpect(status().isOk());

		assertThat(dbTime("SELECT created_at FROM carts")).isEqualTo("2026-03-01 10:15:30.123456");
		assertThat(dbTime("SELECT updated_at FROM carts")).isEqualTo("2026-03-01 10:20:30.123456");
		assertThat(dbTime("SELECT added_at FROM cart_items")).isEqualTo("2026-03-01 10:15:30.123456");
		assertThat(dbTime("SELECT updated_at FROM cart_items")).isEqualTo("2026-03-01 10:20:30.123456");
	}

	// --- uk_carts_active_user yarışı (rakip transaction taklit edilir) ---

	@Test
	void concurrentFirstCartViolationIsRetriedOnceAndJoinsWinnersCart() throws Exception {
		Book book = catalog.publish("Yarış", "10.00");
		doAnswer(invocation -> {
			openCompetingCart();
			return Optional.empty();
		}).doCallRealMethod().when(carts).findActiveByUserIdForUpdate(any());

		add(book, 2).andExpect(status().isOk())
			.andExpect(jsonPath("$.lineCount").value(1))
			.andExpect(jsonPath("$.items[0].quantity").value(2));

		verify(carts, times(2)).findActiveByUserIdForUpdate(userId);
		assertThat(cartCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isEqualTo(1);
	}

	@Test
	void secondActiveCartViolationIsConflict(CapturedOutput output) throws Exception {
		Book book = catalog.publish("Yarış", "10.00");
		doAnswer(invocation -> {
			openCompetingCart();
			return Optional.empty();
		}).doReturn(Optional.empty()).when(carts).findActiveByUserIdForUpdate(any());

		String body = add(book, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andReturn().getResponse().getContentAsString();

		verify(carts, times(2)).findActiveByUserIdForUpdate(userId);
		assertThat(body).doesNotContain(book.id().toString()).doesNotContain(SUBJECT);
		assertThat(cartCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isZero();
		assertThat(output).contains("POST /api/cart/items -> CONFLICT (constraint=uk_carts_active_user, kind=UNIQUE)")
			.doesNotContain(SUBJECT);
	}

	// --- istek doğrulama ---

	@Test
	void requestsWithoutTokenAreUnauthorized() throws Exception {
		mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON)
				.content(body(UUID.randomUUID(), 1)))
			.andExpect(status().isUnauthorized());
		assertThat(CATALOG.requests()).isEmpty();
	}

	@Test
	void missingBookIdIsValidationErrorAndBrokenJsonIsMalformed() throws Exception {
		mockMvc.perform(post("/api/cart/items").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"quantity\":1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("bookId"));
		mockMvc.perform(post("/api/cart/items").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		mockMvc.perform(post("/api/cart/items").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"kitap-degil\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		assertThat(CATALOG.requests()).isEmpty();
		assertThat(cartCount()).isZero();
	}

	// --- loglar ---

	@Test
	void logsContainNoBookIdUserIdTokenOrPriceAndOnlyExpectedWarns(CapturedOutput output) throws Exception {
		Book book = catalog.publish("Log", "149.90");
		Book soldOut = catalog.put(catalog.publish("Tükendi", "77.70").outOfStock());
		add(book, 9).andExpect(status().isOk());
		add(book, 5).andExpect(status().isConflict());
		add(soldOut, 1).andExpect(status().isConflict());
		add(book, 100).andExpect(status().isBadRequest());
		getCart().andExpect(status().isOk());
		catalog.failWith(Response.problem(503));
		getCart().andExpect(status().isOk()).andExpect(jsonPath("$.catalogStatus").value("UNAVAILABLE"));
		add(book, 1).andExpect(status().isServiceUnavailable());

		for (String secret : List.of(book.id().toString(), soldOut.id().toString(), SUBJECT, token, "149.90",
				"77.70", "1349.10")) {
			assertThat(output).doesNotContain(secret);
		}
		assertThat(output.getOut().lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR ")))
			.containsExactly(
					expectedLine(output, "GET /api/cart -> CATALOG_UNAVAILABLE (cause=ServiceUnavailable, served from snapshot)"),
					expectedLine(output, "POST /api/cart/items -> CATALOG_UNAVAILABLE (cause=ServiceUnavailable)"));
	}

	// --- yardımcılar ---

	private ResultActions getCart() throws Exception {
		return mockMvc.perform(get("/api/cart").with(bearer(token)));
	}

	private ResultActions add(Book book, int quantity) throws Exception {
		return add(book.id(), quantity);
	}

	private ResultActions add(UUID bookId, int quantity) throws Exception {
		return mockMvc.perform(post("/api/cart/items").with(bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(bookId, quantity)));
	}

	private static String body(UUID bookId, int quantity) {
		return "{\"bookId\":\"" + bookId + "\",\"quantity\":" + quantity + "}";
	}

	private CartResponse read(String json) {
		CartResponse cart = jsonMapper.readValue(json, CartResponse.class);
		assertThat(fieldsOf(json)).isEqualTo(CART_FIELDS);
		return cart;
	}

	@SuppressWarnings("unchecked")
	private Set<String> fieldsOf(String json) {
		Map<String, Object> root = jsonMapper.readValue(json, Map.class);
		for (Object item : (List<Object>) root.get("items")) {
			assertThat(((Map<String, Object>) item).keySet()).isEqualTo(LINE_FIELDS);
		}
		return root.keySet();
	}

	private void assertNoIdentifiers(String json) {
		assertThat(json).doesNotContain(SUBJECT);
		for (String id : jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM carts UNION ALL "
				+ "SELECT BIN_TO_UUID(id) FROM cart_items", String.class)) {
			assertThat(json).doesNotContain(id);
		}
	}

	private static CartLineResponse line(CartResponse cart, Book book) {
		return cart.items().stream().filter(line -> line.bookId().equals(book.id())).findFirst().orElseThrow();
	}

	/** Rakip isteğin ayrı transaction'da açıp commit ettiği aktif sepet. */
	private void openCompetingCart() {
		TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
		requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		requiresNew.executeWithoutResult(status -> carts.saveAndFlush(Cart.openFor(userId, clock)));
	}

	private int cartCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM carts", Integer.class);
	}

	private String dbTime(String select) {
		String column = select.substring("SELECT ".length(), select.indexOf(" FROM"));
		return jdbc.queryForObject(select.replace(column, "DATE_FORMAT(" + column + ", '%Y-%m-%d %H:%i:%s.%f')"),
				String.class);
	}

	private static String expectedLine(CapturedOutput output, String suffix) {
		return output.getOut().lines().filter(line -> line.endsWith(suffix)).findFirst().orElse("<missing> " + suffix);
	}

}
