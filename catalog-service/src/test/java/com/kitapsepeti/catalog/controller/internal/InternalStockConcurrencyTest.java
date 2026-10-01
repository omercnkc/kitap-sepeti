package com.kitapsepeti.catalog.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Gerçek thread'lerle eşzamanlı rezervasyon. Her senaryoda tüm thread'ler hazır olunca tek bir latch ile aynı anda
 * başlatılır; istekler MockMvc üzerinden gerçek transaction'larla Testcontainers MySQL'e gider.
 */
class InternalStockConcurrencyTest extends InternalStockTestSupport {

	// --- 9. Son kopyalar için yarış

	@Test
	@SuppressWarnings("unchecked")
	void tenOrdersCompeteForThreeCopies() throws Exception {
		UUID book = book("published", 3);
		List<Callable<MockHttpServletResponse>> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			UUID orderId = UUID.randomUUID();
			tasks.add(() -> reserve(request(orderId, book, 1)).andReturn().getResponse());
		}

		List<MockHttpServletResponse> responses = runConcurrently(tasks);

		assertThat(responses).filteredOn(r -> r.getStatus() == 201).hasSize(3);
		List<MockHttpServletResponse> rejected = responses.stream().filter(r -> r.getStatus() == 409).toList();
		assertThat(rejected).hasSize(7);
		for (MockHttpServletResponse response : rejected) {
			String body = response.getContentAsString();
			assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("INSUFFICIENT_STOCK");
			assertThat((List<String>) JsonPath.read(body, "$.bookIds")).containsExactly(book.toString());
		}
		assertThat(reservedOf(book)).isEqualTo(3);
		assertThat(reservationCount()).isEqualTo(3);
	}

	// --- 10. Aynı sipariş aynı anda birden çok kez

	@Test
	void sameOrderSentFiveTimesConcurrentlyReservesOnce() throws Exception {
		UUID book = book("published", 10);
		UUID orderId = UUID.randomUUID();
		List<Callable<MockHttpServletResponse>> tasks = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			tasks.add(() -> reserve(request(orderId, book, 2)).andReturn().getResponse());
		}

		List<MockHttpServletResponse> responses = runConcurrently(tasks);

		assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(201, 200);
		assertThat(responses).filteredOn(r -> r.getStatus() == 201).hasSize(1);
		assertThat(responses).filteredOn(r -> r.getStatus() == 200).hasSize(4);
		for (MockHttpServletResponse response : responses) {
			String body = response.getContentAsString();
			assertThat((String) JsonPath.read(body, "$.orderId")).isEqualTo(orderId.toString());
			assertThat((Integer) JsonPath.read(body, "$.items[0].quantity")).isEqualTo(2);
		}
		assertThat(rowsOf(orderId)).hasSize(1);
		assertThat(reservedOf(book)).isEqualTo(2);
	}

	// --- 11. Ters sırada aynı kitaplar: deadlock olmamalı

	@Test
	void ordersWithReversedItemOrderDoNotDeadlock() throws Exception {
		UUID a = book("published", 100);
		UUID b = book("published", 100);
		for (int round = 0; round < 20; round++) {
			UUID first = UUID.randomUUID();
			UUID second = UUID.randomUUID();
			List<MockHttpServletResponse> responses = runConcurrently(List.of(
					() -> reserve(request(first, a, 1, b, 1)).andReturn().getResponse(),
					() -> reserve(request(second, b, 1, a, 1)).andReturn().getResponse()));
			assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(201);
		}
		assertThat(reservedOf(a)).isEqualTo(40);
		assertThat(reservedOf(b)).isEqualTo(40);
		assertThat(reservationCount()).isEqualTo(80);
	}

	@Test
	void concurrentCommitReleaseAndReserveOnSameBooksDoNotDeadlock() throws Exception {
		UUID a = book("published", 100);
		UUID b = book("published", 100);
		for (int round = 0; round < 20; round++) {
			UUID toCommit = UUID.randomUUID();
			UUID toRelease = UUID.randomUUID();
			reserve(request(toCommit, a, 1, b, 1));
			reserve(request(toRelease, b, 1, a, 1));
			UUID fresh = UUID.randomUUID();
			List<MockHttpServletResponse> responses = runConcurrently(List.of(
					() -> commit(toCommit).andReturn().getResponse(),
					() -> release(toRelease).andReturn().getResponse(),
					() -> reserve(request(fresh, b, 1, a, 1)).andReturn().getResponse()));
			assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactly(200, 200, 201);
		}
		// Her turda: biri onaylandı (stok -1), biri serbest, biri hâlâ held.
		assertThat(stockOf(a)).isEqualTo(80);
		assertThat(reservedOf(a)).isEqualTo(20);
		assertThat(stockOf(b)).isEqualTo(80);
		assertThat(reservedOf(b)).isEqualTo(20);
	}

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
