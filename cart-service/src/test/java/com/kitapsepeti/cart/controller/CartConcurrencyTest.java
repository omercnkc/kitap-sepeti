package com.kitapsepeti.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.entity.Cart;
import com.kitapsepeti.cart.support.FakeCatalog;
import com.kitapsepeti.cart.support.FakeCatalog.Book;
import com.kitapsepeti.cart.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Aynı kullanıcının eşzamanlı sepet istekleri: ilk sepet yarışı ({@code uk_carts_active_user} + yeniden deneme),
 * satır kilidiyle sıraya giren limit kontrolleri ve aynı satırda PATCH/DELETE/boşaltma yarışları. Hiçbir senaryoda
 * 500, deadlock ya da kilit zaman aşımı olmamalı.
 */
class CartConcurrencyTest extends ApiTestSupport {

	private final FakeCatalog catalog = new FakeCatalog();

	private final UUID userId = UUID.fromString(SUBJECT);

	private String token;

	private record Outcome(int status, String code) {
	}

	@BeforeEach
	void useFakeCatalog() {
		CATALOG.respondWith(catalog);
		token = TestJwt.user(SUBJECT);
	}

	@Test
	void twoFirstAddsOfDifferentBooksShareOneNewCart() throws Exception {
		Book first = catalog.publish("Bir", "10.00");
		Book second = catalog.publish("İki", "20.00");

		List<Outcome> outcomes = runConcurrently(List.of(() -> add(first), () -> add(second)));

		assertThat(outcomes).extracting(Outcome::status).containsOnly(200);
		assertThat(activeCartCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isEqualTo(2);
	}

	@Test
	void tenConcurrentAddsOfSameBookFromEmptyEndAtQuantityTen() throws Exception {
		Book book = catalog.publish("Aynı", "10.00");

		List<Outcome> outcomes = runConcurrently(tasks(10, i -> book));

		assertThat(outcomes).extracting(Outcome::status).containsOnly(200);
		assertThat(activeCartCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(10);
	}

	@Test
	void twelveConcurrentAddsGiveExactlyTenSuccessesAndTwoQuantityConflicts() throws Exception {
		Book book = catalog.publish("Sınırda", "10.00");

		List<Outcome> outcomes = runConcurrently(tasks(12, i -> book));

		assertThat(outcomes).filteredOn(outcome -> outcome.status() == 200).hasSize(10);
		assertThat(outcomes).filteredOn(outcome -> outcome.status() != 200)
			.containsExactly(new Outcome(409, "CART_QUANTITY_LIMIT_EXCEEDED"),
					new Outcome(409, "CART_QUANTITY_LIMIT_EXCEEDED"));
		assertThat(activeCartCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(10);
	}

	@Test
	void fiveNewBooksIntoCartWithOneFreeLineGiveOneSuccessAndFourLineConflicts() throws Exception {
		Cart cart = Cart.openFor(userId, clock);
		for (int i = 0; i < 49; i++) {
			cart.addItem(UUID.randomUUID(), 1, new BigDecimal("5.00"), "TRY", "Dolgu " + i, null, clock);
		}
		carts.saveAndFlush(cart);
		List<Book> fresh = IntStream.range(0, 5).mapToObj(i -> catalog.publish("Yeni " + i, "10.00")).toList();

		List<Outcome> outcomes = runConcurrently(tasks(5, fresh::get));

		assertThat(outcomes).filteredOn(outcome -> outcome.status() == 200).hasSize(1);
		assertThat(outcomes).filteredOn(outcome -> outcome.status() != 200)
			.hasSize(4)
			.containsOnly(new Outcome(409, "CART_LINE_LIMIT_EXCEEDED"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isEqualTo(50);
	}

	@Test
	void fiveConcurrentPatchesOfSameLineLeaveOneOfTheRequestedQuantities() throws Exception {
		Book book = catalog.publish("Adet yarışı", "10.00");
		seedCartWith(book);
		List<Integer> quantities = List.of(2, 3, 4, 5, 6);

		List<Outcome> outcomes = runConcurrently(
				quantities.stream().<Callable<Outcome>>map(quantity -> () -> patchQuantity(book, quantity)).toList());

		assertThat(outcomes).extracting(Outcome::status).containsOnly(200);
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isIn(quantities);
	}

	/** Sıra ne olursa olsun satır sonunda yoktur: önce PATCH ise sonra silinir, önce DELETE ise PATCH 404 alır. */
	@Test
	void concurrentDeleteAndPatchOfSameLineEndWithLineRemoved() throws Exception {
		Book book = catalog.publish("Sil-değiştir", "10.00");
		for (int round = 0; round < 5; round++) {
			resetCarts();
			seedCartWith(book);

			List<Outcome> outcomes = runConcurrently(List.of(() -> removeItem(book), () -> patchQuantity(book, 7)));

			assertThat(outcomes.get(0)).isEqualTo(new Outcome(200, null));
			assertThat(outcomes.get(1)).isIn(new Outcome(200, null), new Outcome(404, "RESOURCE_NOT_FOUND"));
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isZero();
			assertThat(activeCartCount()).isEqualTo(1);
		}
	}

	/** Önce boşaltma ise yeni kitap kalır; önce ekleme ise boşaltma onu da siler. Sepet her durumda tek ve aktif. */
	@Test
	void concurrentClearAndAddEndWithEmptyCartOrOnlyTheNewBook() throws Exception {
		Book old = catalog.publish("Eski", "10.00");
		Book fresh = catalog.publish("Yeni", "20.00");
		for (int round = 0; round < 5; round++) {
			resetCarts();
			seedCartWith(old);

			List<Outcome> outcomes = runConcurrently(List.of(this::clear, () -> add(fresh)));

			assertThat(outcomes).extracting(Outcome::status).containsOnly(200);
			assertThat(jdbc.queryForList("SELECT BIN_TO_UUID(book_id) FROM cart_items", String.class))
				.isIn(List.of(), List.of(fresh.id().toString()));
			assertThat(activeCartCount()).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Integer.class)).isEqualTo(1);
		}
	}

	private List<Callable<Outcome>> tasks(int count, IntFunction<Book> bookOf) {
		return IntStream.range(0, count).<Callable<Outcome>>mapToObj(i -> () -> add(bookOf.apply(i))).toList();
	}

	private Outcome add(Book book) throws Exception {
		return outcomeOf(post("/api/cart/items").with(bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"bookId\":\"" + book.id() + "\",\"quantity\":1}"));
	}

	private Outcome patchQuantity(Book book, int quantity) throws Exception {
		return outcomeOf(patch("/api/cart/items/{bookId}", book.id()).with(bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"quantity\":" + quantity + "}"));
	}

	private Outcome removeItem(Book book) throws Exception {
		return outcomeOf(delete("/api/cart/items/{bookId}", book.id()).with(bearer(token)));
	}

	private Outcome clear() throws Exception {
		return outcomeOf(delete("/api/cart/items").with(bearer(token)));
	}

	private Outcome outcomeOf(RequestBuilder request) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
		String code = (response.getStatus() == 200) ? null
				: JsonPath.read(response.getContentAsString(), "$.code");
		return new Outcome(response.getStatus(), code);
	}

	private void seedCartWith(Book book) {
		Cart cart = Cart.openFor(userId, clock);
		cart.addItem(book.id(), 1, new BigDecimal(book.price()), "TRY", book.title(), null, clock);
		carts.saveAndFlush(cart);
	}

	private void resetCarts() {
		jdbc.update("DELETE FROM cart_items");
		jdbc.update("DELETE FROM carts");
	}

	private int activeCartCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE status = 'active' AND user_id = UUID_TO_BIN(?)",
				Integer.class, userId.toString());
	}

	/** Görevler ayrı thread'lerde; hepsi hazır olunca tek latch ile aynı anda başlatılır. Sonuçlar görev sırasıyla. */
	private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		try {
			CountDownLatch ready = new CountDownLatch(tasks.size());
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return task.call();
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
