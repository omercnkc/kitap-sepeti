package com.kitapsepeti.payment.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.kitapsepeti.payment.config.PaymentProperties;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.exception.WebhookSignatureException;
import com.kitapsepeti.payment.provider.MockOutcomeRule;
import com.kitapsepeti.payment.provider.mock.MockWebhookDispatcher.Delivery;
import com.kitapsepeti.payment.repository.PaymentRepository;
import com.kitapsepeti.payment.support.InternalTestKeys;
import com.kitapsepeti.payment.support.MutableClock;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gönderici, Spring bağlamı olmadan: gerçek HTTP ile yerel bir stub sunucuya gönderir. Stub isteği gerçek
 * {@link MockWebhookVerifier} ile doğrular (webhook ucunun imza adımıyla aynı).
 */
@ExtendWith(OutputCaptureExtension.class)
class MockWebhookDispatcherTest {

	private static final String SECRET = InternalTestKeys.randomKey();

	private final MutableClock clock = new MutableClock();

	private final MockWebhookSigner signer = new MockWebhookSigner(SECRET);

	private final PaymentRepository payments = mock(PaymentRepository.class);

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final List<Received> received = new CopyOnWriteArrayList<>();

	private HttpServer server;

	private volatile int responseStatus = 204;

	private volatile Duration responseDelay = Duration.ZERO;

	private volatile MockWebhookVerifier verifier;

	private MockWebhookDispatcher dispatcher;

	record Received(Map<String, List<String>> headers, byte[] body) {

		String header(String name) {
			return headers.entrySet()
				.stream()
				.filter(entry -> entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty())
				.map(entry -> entry.getValue().get(0))
				.findFirst()
				.orElse(null);
		}

	}

	@BeforeEach
	void startServer() throws IOException {
		clock.fixAt(Instant.parse("2026-10-04T10:00:00Z"));
		verifier = new MockWebhookVerifier(signer, Duration.ofMinutes(5), clock);
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext(MockWebhookDispatcher.WEBHOOK_PATH, this::handle);
		server.start();
	}

	@AfterEach
	void stop() {
		if (dispatcher != null) {
			dispatcher.destroy();
		}
		server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		byte[] body = exchange.getRequestBody().readAllBytes();
		received.add(new Received(Map.copyOf(exchange.getRequestHeaders()), body));
		int status = responseStatus;
		if (status == 204) {
			try {
				verifier.verify(exchange.getRequestHeaders().getFirst(MockWebhookSigner.TIMESTAMP_HEADER),
						exchange.getRequestHeaders().getFirst(MockWebhookSigner.SIGNATURE_HEADER), body);
			}
			catch (WebhookSignatureException ex) {
				status = 401;
			}
		}
		try {
			Thread.sleep(responseDelay.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		exchange.sendResponseHeaders(status, -1);
		exchange.close();
	}

	private String serverUrl() {
		return "http://localhost:" + server.getAddress().getPort() + MockWebhookDispatcher.WEBHOOK_PATH;
	}

	private MockWebhookDispatcher dispatcher(String url, boolean dispatchEnabled, Duration delay, int capacity) {
		PaymentProperties.Mock settings = new PaymentProperties.Mock(99, SECRET, delay, url,
				new PaymentProperties.Dispatch(dispatchEnabled, capacity),
				new PaymentProperties.Recovery(true, Duration.ofSeconds(30), Duration.ofSeconds(10), 50));
		dispatcher = new MockWebhookDispatcher(payments, new MockOutcomeRule(99), signer, jsonMapper, clock, settings);
		return dispatcher;
	}

	private MockWebhookDispatcher dispatcher() {
		return dispatcher(serverUrl(), false, Duration.ZERO, 100);
	}

	private Payment payment(String amount, boolean withReference, PaymentProviderType provider) {
		Payment payment = Payment.initiate(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(amount), "TRY",
				provider, clock);
		ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
		if (withReference) {
			payment.attachProviderReference("mock_" + UUID.randomUUID(), clock);
		}
		when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
		return payment;
	}

	private Payment payment(String amount) {
		return payment(amount, true, PaymentProviderType.MOCK);
	}

	@Test
	void succeededOutcomeIsSentOnceAsSignedJsonWithFixedEventId() {
		Payment payment = payment("10.00");

		assertThat(dispatcher().send(payment.getId())).isEqualTo(Delivery.DELIVERED);

		assertThat(received).hasSize(1);
		Received request = received.get(0);
		assertThat(request.header("Content-type")).isEqualTo("application/json");
		String timestamp = request.header(MockWebhookSigner.TIMESTAMP_HEADER);
		assertThat(timestamp).isEqualTo(Long.toString(clock.instant().getEpochSecond()));
		assertThat(request.header(MockWebhookSigner.SIGNATURE_HEADER)).isEqualTo(signer.sign(Long.parseLong(timestamp),
				request.body()));
		JsonNode body = jsonMapper.readTree(request.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("eventId", "providerPaymentId", "type", "amount",
				"currency");
		assertThat(body.get("eventId").asString()).isEqualTo("mock_evt_" + payment.getId());
		assertThat(body.get("providerPaymentId").asString()).isEqualTo(payment.getProviderPaymentId());
		assertThat(body.get("type").asString()).isEqualTo("payment.succeeded");
		assertThat(body.get("amount").isString()).isTrue();
		assertThat(body.get("amount").asString()).isEqualTo("10.00");
		assertThat(body.get("currency").asString()).isEqualTo("TRY");
	}

	@Test
	void failCentsOutcomeIsSentAsFailedWithCardDeclined() {
		Payment payment = payment("10.99");

		assertThat(dispatcher().send(payment.getId())).isEqualTo(Delivery.DELIVERED);

		JsonNode body = jsonMapper.readTree(received.get(0).body());
		assertThat(body.get("type").asString()).isEqualTo("payment.failed");
		assertThat(body.get("failureCode").asString()).isEqualTo("CARD_DECLINED");
		assertThat(body.get("amount").asString()).isEqualTo("10.99");
	}

	@Test
	void resendingTheSamePaymentRepeatsTheSameEventId() {
		Payment payment = payment("10.00");
		MockWebhookDispatcher dispatcher = dispatcher();

		dispatcher.send(payment.getId());
		clock.advance(Duration.ofSeconds(30));
		dispatcher.send(payment.getId());

		assertThat(received).hasSize(2);
		assertThat(jsonMapper.readTree(received.get(0).body()).get("eventId"))
			.isEqualTo(jsonMapper.readTree(received.get(1).body()).get("eventId"));
	}

	@Test
	void nothingIsSentForMissingFinalReferencelessOrNonMockPayments() {
		MockWebhookDispatcher dispatcher = dispatcher();
		UUID missing = UUID.randomUUID();
		when(payments.findById(missing)).thenReturn(Optional.empty());
		Payment succeeded = payment("10.00");
		succeeded.succeed(clock);
		Payment failed = payment("10.99");
		failed.fail("CARD_DECLINED", clock);
		Payment withoutReference = payment("10.00", false, PaymentProviderType.MOCK);
		Payment otherProvider = payment("10.00", true, PaymentProviderType.IYZICO);

		for (UUID id : List.of(missing, succeeded.getId(), failed.getId(), withoutReference.getId(),
				otherProvider.getId())) {
			assertThat(dispatcher.send(id)).isEqualTo(Delivery.SKIPPED);
		}
		assertThat(received).isEmpty();
	}

	@Test
	void clientErrorIsRejectedWithoutRetry(CapturedOutput output) {
		verifier = new MockWebhookVerifier(new MockWebhookSigner(InternalTestKeys.randomKey()), Duration.ofMinutes(5),
				clock);
		Payment payment = payment("10.00");

		assertThat(dispatcher().send(payment.getId())).isEqualTo(Delivery.REJECTED);

		assertThat(received).hasSize(1);
		assertThat(output).contains("Mock webhook rejected (status=401)");
	}

	@Test
	void serverErrorIsReportedAsFailed(CapturedOutput output) {
		responseStatus = 503;
		Payment payment = payment("10.00");

		assertThat(dispatcher().send(payment.getId())).isEqualTo(Delivery.FAILED);

		assertThat(received).hasSize(1);
		assertThat(output).contains("Mock webhook delivery failed (status=503)");
	}

	@Test
	void closedPortIsReportedAsFailedWithoutException(CapturedOutput output) throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		Payment payment = payment("10.00");

		Delivery delivery = dispatcher("http://localhost:" + closedPort + "/webhooks/mock", false, Duration.ZERO, 100)
			.send(payment.getId());

		assertThat(delivery).isEqualTo(Delivery.FAILED);
		assertThat(output).contains("Mock webhook delivery failed (cause=");
	}

	@Test
	void slowResponseTimesOutAfterReadTimeout(CapturedOutput output) {
		responseDelay = Duration.ofSeconds(4);
		Payment payment = payment("10.00");
		long started = System.nanoTime();

		Delivery delivery = dispatcher().send(payment.getId());

		assertThat(delivery).isEqualTo(Delivery.FAILED);
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(3500));
		assertThat(output).contains("Mock webhook delivery failed (cause=");
	}

	@Test
	void databaseErrorIsReportedAsFailedWithoutException(CapturedOutput output) {
		when(payments.findById(any())).thenThrow(new DataAccessResourceFailureException("db down"));

		assertThat(dispatcher().send(UUID.randomUUID())).isEqualTo(Delivery.FAILED);

		assertThat(output).contains("Mock webhook delivery failed (cause=DataAccessResourceFailureException)");
	}

	@Test
	void withoutConfiguredUrlOrStartedServerNothingIsSent(CapturedOutput output) {
		Payment payment = payment("10.00");

		assertThat(dispatcher(null, false, Duration.ZERO, 100).send(payment.getId())).isEqualTo(Delivery.FAILED);

		assertThat(received).isEmpty();
		assertThat(output).contains("Mock webhook delivery failed (webhook URL not known yet)");
	}

	@Test
	void readyEventIsSentAfterTheDelayWithoutBlockingTheCaller() {
		Payment payment = payment("10.00");
		MockWebhookDispatcher dispatcher = dispatcher(serverUrl(), true, Duration.ofMillis(700), 100);
		long started = System.nanoTime();

		dispatcher.onPaymentReady(new MockPaymentReadyEvent(payment.getId()));

		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(700));
		assertThat(received).isEmpty();
		await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 1);
		await().atMost(Duration.ofSeconds(5)).until(() -> dispatcher.pendingCount() == 0);
	}

	@Test
	void fullQueueDropsTheNewDispatchWithWarning(CapturedOutput output) {
		Payment first = payment("10.00");
		Payment second = payment("20.00");
		MockWebhookDispatcher dispatcher = dispatcher(serverUrl(), true, Duration.ofMinutes(10), 1);

		dispatcher.onPaymentReady(new MockPaymentReadyEvent(first.getId()));
		dispatcher.onPaymentReady(new MockPaymentReadyEvent(second.getId()));

		assertThat(dispatcher.pendingCount()).isEqualTo(1);
		assertThat(output).contains("Mock webhook dispatch queue is full; webhook dropped, recovery job will resend it");
	}

	@Test
	void disabledDispatchSchedulesNothing() throws InterruptedException {
		Payment payment = payment("10.00");
		MockWebhookDispatcher dispatcher = dispatcher(serverUrl(), false, Duration.ZERO, 100);

		dispatcher.onPaymentReady(new MockPaymentReadyEvent(payment.getId()));
		Thread.sleep(300);

		assertThat(dispatcher.pendingCount()).isZero();
		assertThat(received).isEmpty();
	}

	@Test
	void logsContainNoIdsReferenceAmountSignatureOrAddress(CapturedOutput output) throws IOException {
		Payment delivered = payment("7654.32");
		Payment rejected = payment("7654.33");
		Payment failed = payment("7654.34");
		MockWebhookDispatcher dispatcher = dispatcher();
		dispatcher.send(delivered.getId());
		verifier = new MockWebhookVerifier(new MockWebhookSigner(InternalTestKeys.randomKey()), Duration.ofMinutes(5),
				clock);
		dispatcher.send(rejected.getId());
		responseStatus = 500;
		dispatcher.send(failed.getId());

		assertThat(received).hasSize(3);
		for (Payment payment : List.of(delivered, rejected, failed)) {
			assertThat(output).doesNotContain(payment.getId().toString())
				.doesNotContain(payment.getProviderPaymentId())
				.doesNotContain(payment.getAmount().toPlainString());
		}
		assertThat(output).doesNotContain("mock_evt_")
			.doesNotContain("sha256=")
			.doesNotContain(Integer.toString(server.getAddress().getPort()))
			.doesNotContain(SECRET);
	}

}
