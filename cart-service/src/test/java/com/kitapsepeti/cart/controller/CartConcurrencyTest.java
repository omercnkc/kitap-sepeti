package com.kitapsepeti.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Aynı kullanıcının eşzamanlı {@code POST /api/cart/items} istekleri: ilk sepet yarışı ({@code uk_carts_active_user}
 * + yeniden deneme) ve satır kilidiyle sıraya giren limit kontrolleri. Hiçbir senaryoda 500, deadlock ya da kilit
 * zaman aşımı olmamalı.
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

	private List<Callable<Outcome>> tasks(int count, IntFunction<Book> bookOf) {
		return IntStream.range(0, count).<Callable<Outcome>>mapToObj(i -> () -> add(bookOf.apply(i))).toList();
	}

	private Outcome add(Book book) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(post("/api/cart/items").with(bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + book.id() + "\",\"quantity\":1}"))
			.andReturn()
			.getResponse();
		String code = (response.getStatus() == 200) ? null
				: JsonPath.read(response.getContentAsString(), "$.code");
		return new Outcome(response.getStatus(), code);
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
