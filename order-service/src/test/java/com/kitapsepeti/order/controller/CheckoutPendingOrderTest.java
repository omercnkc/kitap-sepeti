package com.kitapsepeti.order.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.order.entity.OrderReasons;
import com.kitapsepeti.order.service.OrderTransactions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Kullanıcı başına tek bekleyen sipariş: eşzamanlı checkout'larda {@code uk_orders_pending_user} kaybedene 409 ve
 * kazananın id'si; bekleyen sipariş sonuçlanınca yeni checkout serbest.
 */
class CheckoutPendingOrderTest extends CheckoutTestSupport {

	private record Outcome(int status, String code, String id, String orderId) {
	}

	@Test
	void concurrentCheckoutsCreateOneOrder() throws Exception {
		Book book = stubHappyPath();
		// İki istek de bekleyen sipariş kontrolünü geçsin: yarış kayıtta (unique index) çözülür.
		stubLookup(json(200, "{\"items\":[" + book.json() + "]}").withFixedDelay(400));

		List<Outcome> outcomes = runConcurrently(2);

		assertThat(CATALOG.server().findAll(getRequestedFor(urlPathEqualTo(LOOKUP)))).hasSize(2);
		Outcome created = outcomes.stream().filter(outcome -> outcome.status() == 201).findFirst().orElseThrow();
		Outcome conflict = outcomes.stream().filter(outcome -> outcome.status() != 201).findFirst().orElseThrow();
		assertThat(conflict.status()).isEqualTo(409);
		assertThat(conflict.code()).isEqualTo("ORDER_PENDING_EXISTS");
		assertThat(conflict.orderId()).isEqualTo(created.id());

		assertThat(orderCount()).isEqualTo(1);
		assertThat(orderRow(UUID.fromString(created.id()))).containsEntry("s", "pending").containsEntry("st", "held");
		assertThat(reserveRequests()).hasSize(1);
		assertThat(paymentRequests()).hasSize(1);
	}

	@Test
	void pendingOrderBlocksCheckoutUntilItIsSettled() throws Exception {
		stubHappyPath();
		UUID first = idOf(checkout().andExpect(status().isCreated()));

		checkout().andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PENDING_EXISTS"))
			.andExpect(jsonPath("$.orderId").value(first.toString()));
		assertThat(orderCount()).isEqualTo(1);

		this.transactions.markFailed(first, OrderReasons.ORDER_EXPIRED);
		// Payment her siparişe ayrı ödeme açar (uk_orders_payment).
		stubPaymentInitiated(UUID.randomUUID(), "initiated");

		UUID second = idOf(checkout().andExpect(status().isCreated()));
		assertThat(second).isNotEqualTo(first);
		assertThat(orderCount()).isEqualTo(2);
		assertThat(orderRow(first)).containsEntry("s", "failed").containsEntry("f", "ORDER_EXPIRED");
		assertThat(orderRow(second)).containsEntry("s", "pending");
	}

	/** Başarısız checkout (sipariş failed) kullanıcıyı engellemez. */
	@Test
	void failedCheckoutDoesNotBlockTheNextOne() throws Exception {
		Book book = stubHappyPath();
		stubReserve(stockProblem("INSUFFICIENT_STOCK", book.id()));
		UUID failed = orderIdOf(checkout().andExpect(status().isConflict()));

		stubReserveHeld();
		UUID placed = idOf(checkout().andExpect(status().isCreated()));

		assertThat(placed).isNotEqualTo(failed);
		assertThat(orderRow(failed)).containsEntry("s", "failed");
		assertThat(orderRow(placed)).containsEntry("s", "pending");
	}

	/** Başka kullanıcının bekleyen siparişi etkilemez. */
	@Test
	void pendingOrderOfAnotherUserDoesNotBlock() throws Exception {
		stubHappyPath();
		checkout().andExpect(status().isCreated());

		resetRemotesAndUser();
		stubHappyPath();
		checkout().andExpect(status().isCreated());
	}

	private List<Outcome> runConcurrently(int count) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(count);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				futures.add(executor.submit(() -> {
					start.await();
					return checkout().andReturn().getResponse();
				}));
			}
			start.countDown();
			List<Outcome> outcomes = new ArrayList<>();
			for (Future<MockHttpServletResponse> future : futures) {
				MockHttpServletResponse response = future.get(30, TimeUnit.SECONDS);
				String body = response.getContentAsString();
				outcomes.add(new Outcome(response.getStatus(), read(body, "$.code"), read(body, "$.id"),
						read(body, "$.orderId")));
			}
			return outcomes;
		}
		finally {
			executor.shutdownNow();
		}
	}

	private static String read(String body, String path) {
		try {
			return JsonPath.read(body, path);
		}
		catch (com.jayway.jsonpath.PathNotFoundException ex) {
			return null;
		}
	}

}
