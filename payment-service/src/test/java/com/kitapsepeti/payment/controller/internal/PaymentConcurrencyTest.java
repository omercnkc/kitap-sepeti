package com.kitapsepeti.payment.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.payment.ApiTestSupport;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.provider.ProviderPaymentRequest;
import com.kitapsepeti.payment.service.PaymentTransactions;
import com.kitapsepeti.payment.support.InternalTestKeys;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Aynı sipariş için eşzamanlı istekler: tek satır, tek ödeme, tek 201; sağlayıcı birden fazla çağrılsa bile ilk yazılan
 * referans korunur ve 500 yok.
 */
@ExtendWith(OutputCaptureExtension.class)
class PaymentConcurrencyTest extends ApiTestSupport {

	private static final String KEEP_WARNING = "Payment already has another provider reference";

	@Autowired
	private PaymentTransactions transactions;

	@Autowired
	private Clock clock;

	@PersistenceContext
	private EntityManager entityManager;

	private record Outcome(int status, String paymentId) {
	}

	@Test
	void tenParallelFirstRequestsForSameOrderCreateExactlyOnePayment(CapturedOutput output) throws Exception {
		UUID orderId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String body = "{\"orderId\":\"" + orderId + "\",\"userId\":\"" + userId
				+ "\",\"amount\":320.40,\"currency\":\"TRY\"}";

		List<Outcome> outcomes = runConcurrently(
				IntStream.range(0, 10).<Callable<Outcome>>mapToObj(i -> () -> create(body)).toList());

		assertThat(outcomes).extracting(Outcome::status).containsOnly(200, 201);
		assertThat(outcomes).filteredOn(outcome -> outcome.status() == 201).hasSize(1);
		assertThat(outcomes).extracting(Outcome::paymentId).containsOnly(outcomes.get(0).paymentId());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id = UUID_TO_BIN(?)", Integer.class,
				orderId.toString())).isEqualTo(1);
		String reference = jdbc.queryForObject(
				"SELECT provider_payment_id FROM payments WHERE order_id = UUID_TO_BIN(?)", String.class,
				orderId.toString());
		assertThat(reference).startsWith("mock_");

		verify(provider, atLeastOnce()).create(any(ProviderPaymentRequest.class));
		long providerCalls = mockingDetails(provider).getInvocations()
			.stream()
			.filter(invocation -> invocation.getMethod().getName().equals("create"))
			.count();
		assertThat(output.toString().split(KEEP_WARNING, -1)).hasSize((int) providerCalls);
		assertThat(output).doesNotContain(reference).doesNotContain(orderId.toString()).doesNotContain(" ERROR ");
		System.out.println("[concurrency] 201 count = 1, provider calls = " + providerCalls);
	}

	/**
	 * Birinci attach satırı FOR UPDATE ile kilitli tutarken ikincisi başlar ve kilitte bekler; birinci commit edince
	 * ikinci, yazılmış referansı görür: ilk referans korunur, ikincisi atılır (WARN), hata yok.
	 */
	@Test
	void concurrentAttachKeepsTheFirstReference(CapturedOutput output) throws Exception {
		Payment payment = payments.saveAndFlush(Payment.initiate(UUID.randomUUID(), UUID.randomUUID(),
				new BigDecimal("99.00"), "TRY", PaymentProviderType.MOCK, clock));
		UUID paymentId = payment.getId();
		String first = "mock_" + UUID.randomUUID();
		String second = "mock_" + UUID.randomUUID();

		CountDownLatch firstHoldsLock = new CountDownLatch(1);
		CountDownLatch secondEntered = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		AtomicBoolean firstCall = new AtomicBoolean(true);
		doAnswer(invocation -> {
			if (firstCall.getAndSet(false)) {
				Optional<Payment> locked = findForUpdate(paymentId);
				firstHoldsLock.countDown();
				assertThat(releaseFirst.await(30, TimeUnit.SECONDS)).isTrue();
				return locked;
			}
			secondEntered.countDown();
			return findForUpdate(paymentId);
		}).when(payments).findByIdForUpdate(paymentId);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Payment> firstAttach = pool.submit(() -> transactions.attachReference(paymentId, first));
			if (!firstHoldsLock.await(30, TimeUnit.SECONDS)) {
				firstAttach.get(1, TimeUnit.SECONDS);
				fail("first attach did not lock the payment row");
			}
			Future<Payment> secondAttach = pool.submit(() -> transactions.attachReference(paymentId, second));
			assertThat(secondEntered.await(30, TimeUnit.SECONDS)).isTrue();
			Thread.sleep(200);
			assertThat(secondAttach.isDone()).isFalse();
			releaseFirst.countDown();

			assertThat(firstAttach.get(60, TimeUnit.SECONDS).getProviderPaymentId()).isEqualTo(first);
			assertThat(secondAttach.get(60, TimeUnit.SECONDS).getProviderPaymentId()).isEqualTo(first);
		}
		finally {
			releaseFirst.countDown();
			pool.shutdownNow();
		}

		assertThat(jdbc.queryForObject("SELECT provider_payment_id FROM payments WHERE id = UUID_TO_BIN(?)",
				String.class, paymentId.toString())).isEqualTo(first);
		assertThat(output.toString().split(KEEP_WARNING, -1)).hasSize(2);
		assertThat(output).doesNotContain(first).doesNotContain(second).doesNotContain(paymentId.toString());
	}

	/** {@code findByIdForUpdate}'in JPA karşılığı; çağıran thread'in transaction'ında satırı kilitler. */
	private Optional<Payment> findForUpdate(UUID paymentId) {
		return Optional.ofNullable(entityManager.find(Payment.class, paymentId, LockModeType.PESSIMISTIC_WRITE));
	}

	private Outcome create(String body) throws Exception {
		MockHttpServletResponse response = mockMvc
			.perform(post("/internal/payments").header(InternalApiKeyAuthenticationFilter.HEADER,
					InternalTestKeys.ORDER_SERVICE_KEY).contentType(MediaType.APPLICATION_JSON).content(body))
			.andReturn()
			.getResponse();
		String paymentId = (response.getStatus() < 300) ? JsonPath.read(response.getContentAsString(), "$.paymentId")
				: null;
		return new Outcome(response.getStatus(), paymentId);
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
