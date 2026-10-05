package com.kitapsepeti.cart.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.config.CartCheckoutsConsumerConfig;
import com.kitapsepeti.cart.entity.Cart;
import com.kitapsepeti.cart.service.CartTransactions;
import com.kitapsepeti.cart.support.FakeCatalog;
import com.kitapsepeti.cart.support.FakeCatalog.Book;
import com.kitapsepeti.cart.support.TestJwt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** CartCheckedOut consumer'ı gerçek RabbitMQ ve MySQL ile (ayrı bağlam: listener açık). */
@TestPropertySource(properties = "app.cart-checkouts.enabled=true")
@ExtendWith(OutputCaptureExtension.class)
class CartCheckedOutListenerIT extends ApiTestSupport {

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private static final Instant T1 = Instant.parse("2026-10-05T07:00:00.123456Z");

	@Autowired
	private RabbitTemplate rabbit;

	@Autowired
	private AmqpAdmin admin;

	@Autowired
	private TopicExchange eventsExchange;

	@Autowired
	@Qualifier("cartCheckoutsTopology")
	private Declarables topology;

	@Autowired
	private RabbitListenerEndpointRegistry listenerRegistry;

	@Autowired
	private RabbitMQContainer broker;

	@MockitoSpyBean
	private CartTransactions transactions;

	private final UUID userId = UUID.fromString(SUBJECT);

	@BeforeEach
	void clean() {
		this.admin.purgeQueue(CartCheckoutsConsumerConfig.QUEUE, true);
		this.admin.purgeQueue(CartCheckoutsConsumerConfig.DEAD_LETTER_QUEUE, true);
		clock.fixAt(T1);
		clearInvocations(this.transactions);
	}

	@AfterEach
	void resetSpy() {
		reset(this.transactions);
	}

	@Test
	void activeCartIsCheckedOutAndNoLongerShownAsActive() throws Exception {
		UUID cartId = activeCartWithOneLine();
		String token = TestJwt.user(SUBJECT);
		mockMvc.perform(get("/api/cart").with(bearer(token))).andExpect(jsonPath("$.lineCount").value(1));
		clock.advance(Duration.ofMinutes(5));

		publish(checkedOut(cartId, this.userId));

		awaitStatus(cartId, "checked_out");
		assertThat(updatedAt(cartId)).isEqualTo("2026-10-05T07:05:00.123456Z");
		assertThat(itemCount(cartId)).isEqualTo(1);
		mockMvc.perform(get("/api/cart").with(bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.lineCount").value(0));
		assertThat(messageCount(CartCheckoutsConsumerConfig.DEAD_LETTER_QUEUE)).isZero();
	}

	@Test
	void duplicateDeliveryIsAckedWithoutSecondWrite(CapturedOutput output) {
		UUID cartId = activeCartWithOneLine();
		Message message = checkedOut(cartId, this.userId);

		publish(message);
		awaitStatus(cartId, "checked_out");
		String firstUpdate = updatedAt(cartId);
		clock.advance(Duration.ofMinutes(5));
		publish(message);

		await().atMost(TIMEOUT).untilAsserted(() -> verify(this.transactions, times(2)).checkOut(any(), any()));
		await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(cartStatus(cartId)).isEqualTo("checked_out");
			assertThat(updatedAt(cartId)).isEqualTo(firstUpdate);
			assertThat(messageCount(CartCheckoutsConsumerConfig.QUEUE)).isZero();
			assertThat(messageCount(CartCheckoutsConsumerConfig.DEAD_LETTER_QUEUE)).isZero();
		});
		assertThat(output).contains("Cart checkout -> CHECKED_OUT").contains("Cart checkout -> ALREADY_CHECKED_OUT");
	}

	@Test
	void addAfterCheckoutOpensNewActiveCartAndOldStaysCheckedOut() throws Exception {
		FakeCatalog catalog = new FakeCatalog();
		CATALOG.respondWith(catalog);
		Book book = catalog.publish("Yeni sepet", "12.50");
		UUID oldCartId = activeCartWithOneLine();
		publish(checkedOut(oldCartId, this.userId));
		awaitStatus(oldCartId, "checked_out");

		mockMvc.perform(post("/api/cart/items").with(bearer(TestJwt.user(SUBJECT)))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"bookId\":\"" + book.id() + "\",\"quantity\":1}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lineCount").value(1))
			.andExpect(jsonPath("$.items[0].title").value("Yeni sepet"));

		assertThat(cartStatus(oldCartId)).isEqualTo("checked_out");
		assertThat(itemCount(oldCartId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM carts WHERE user_id = UUID_TO_BIN(?) AND status = 'active' AND id <> UUID_TO_BIN(?)""",
				Integer.class, this.userId.toString(), oldCartId.toString())).isEqualTo(1);
	}

	enum PoisonCase {
		MALFORMED_JSON,
		UNKNOWN_TYPE,
		UNSUPPORTED_VERSION,
		MISSING_FIELD,
		UNKNOWN_CART,
		OTHER_USERS_CART
	}

	static Stream<PoisonCase> poisonCases() {
		return Stream.of(PoisonCase.values());
	}

	@ParameterizedTest
	@MethodSource("poisonCases")
	void poisonMessagesGoToDlqWithoutRetryAndLeaveCartUnchanged(PoisonCase poisonCase) {
		UUID cartId = activeCartWithOneLine();
		String before = updatedAt(cartId);
		Message message = switch (poisonCase) {
			case MALFORMED_JSON -> message("CartCheckedOut", "{");
			case UNKNOWN_TYPE -> message("CartAbandoned", body(cartId, this.userId, 1));
			case UNSUPPORTED_VERSION -> message("CartCheckedOut", body(cartId, this.userId, 2));
			case MISSING_FIELD -> message("CartCheckedOut", """
					{"eventId":"%s","eventVersion":1,"userId":"%s","orderId":"%s","occurredAt":"2026-10-05T07:00:00Z"}"""
				.formatted(UUID.randomUUID(), this.userId, UUID.randomUUID()));
			case UNKNOWN_CART -> checkedOut(UUID.randomUUID(), this.userId);
			case OTHER_USERS_CART -> checkedOut(cartId, UUID.randomUUID());
		};

		publish(message);

		await().atMost(TIMEOUT).untilAsserted(() ->
			assertThat(messageCount(CartCheckoutsConsumerConfig.DEAD_LETTER_QUEUE)).isEqualTo(1));
		assertThat(messageCount(CartCheckoutsConsumerConfig.QUEUE)).isZero();
		assertThat(cartStatus(cartId)).isEqualTo("active");
		assertThat(updatedAt(cartId)).isEqualTo(before);
		int attempts = switch (poisonCase) {
			case UNKNOWN_CART, OTHER_USERS_CART -> 1;
			default -> 0;
		};
		verify(this.transactions, times(attempts)).checkOut(any(), any());
	}

	@Test
	void ownerMismatchIsLoggedAsErrorWithoutIds(CapturedOutput output) {
		UUID cartId = activeCartWithOneLine();
		UUID otherUser = UUID.randomUUID();

		publish(checkedOut(cartId, otherUser));

		await().atMost(TIMEOUT).untilAsserted(() ->
			assertThat(messageCount(CartCheckoutsConsumerConfig.DEAD_LETTER_QUEUE)).isEqualTo(1));
		assertThat(output.getOut().lines().filter(line -> line.contains("reason=CART_OWNER_MISMATCH")))
			.singleElement()
			.satisfies(line -> assertThat(line).contains(" ERROR "));
		assertThat(output.getAll()).doesNotContain(cartId.toString()).doesNotContain(otherUser.toString());
	}

	@Test
	void transientFailureIsRetriedAndThenSucceeds() {
		UUID cartId = activeCartWithOneLine();
		doThrow(new CannotAcquireLockException("temporary")).doCallRealMethod()
			.when(this.transactions)
			.checkOut(any(), any());

		publish(checkedOut(cartId, this.userId));

		awaitStatus(cartId, "checked_out");
		verify(this.transactions, times(2)).checkOut(any(), any());
		assertThat(messageCount(CartCheckoutsConsumerConfig.DEAD_LETTER_QUEUE)).isZero();
	}

	@Test
	void persistentTransientFailureIsTriedThreeTimesThenDeadLettered(CapturedOutput output) {
		UUID cartId = activeCartWithOneLine();
		doThrow(new CannotAcquireLockException("temporary")).when(this.transactions).checkOut(any(), any());

		publish(checkedOut(cartId, this.userId));

		await().atMost(TIMEOUT).untilAsserted(() ->
			assertThat(messageCount(CartCheckoutsConsumerConfig.DEAD_LETTER_QUEUE)).isEqualTo(1));
		verify(this.transactions, times(3)).checkOut(any(), any());
		assertThat(cartStatus(cartId)).isEqualTo("active");
		assertThat(output).contains("reason=RETRY_EXHAUSTED");
	}

	@Test
	void abandonedCartIsAckedAndLeftUnchanged(CapturedOutput output) {
		Cart cart = Cart.openFor(this.userId, clock);
		cart.addItem(UUID.randomUUID(), 1, new BigDecimal("10.00"), "TRY", "Terk", null, clock);
		cart.abandon(clock);
		UUID cartId = carts.saveAndFlush(cart).getId();
		clock.advance(Duration.ofMinutes(5));

		publish(checkedOut(cartId, this.userId));

		await().atMost(TIMEOUT).untilAsserted(() -> verify(this.transactions).checkOut(any(), any()));
		await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(messageCount(CartCheckoutsConsumerConfig.QUEUE)).isZero();
			assertThat(messageCount(CartCheckoutsConsumerConfig.DEAD_LETTER_QUEUE)).isZero();
		});
		assertThat(cartStatus(cartId)).isEqualTo("abandoned");
		assertThat(updatedAt(cartId)).isEqualTo("2026-10-05T07:00:00.123456Z");
		assertThat(output.getOut().lines().filter(line -> line.contains("CART_ABANDONED, cart unchanged")))
			.singleElement()
			.satisfies(line -> assertThat(line).contains(" WARN "));
	}

	@Test
	void topologyAndListenerContainerMatchTheConsumerContract() throws Exception {
		assertThat(this.admin.getQueueInfo(CartCheckoutsConsumerConfig.QUEUE)).isNotNull();
		assertThat(this.admin.getQueueInfo(CartCheckoutsConsumerConfig.DEAD_LETTER_QUEUE)).isNotNull();

		Queue declaredWork = this.topology.getDeclarablesByType(Queue.class)
			.stream()
			.filter(queue -> queue.getName().equals(CartCheckoutsConsumerConfig.QUEUE))
			.findFirst()
			.orElseThrow();
		assertThat(declaredWork.isDurable()).isTrue();
		assertThat(declaredWork.getArguments())
			.containsEntry("x-dead-letter-exchange", "kitapsepeti.dlx")
			.containsEntry("x-dead-letter-routing-key", "cart.checkouts.dead");
		assertThat(this.topology.getDeclarablesByType(DirectExchange.class)).extracting(DirectExchange::getName)
			.containsExactly("kitapsepeti.dlx");
		assertThat(this.topology.getDeclarablesByType(Binding.class)).extracting(Binding::getRoutingKey)
			.containsExactly("cart.checkouts.dead", "cart.checked-out");

		// Broker'daki gerçek durum.
		assertThat(rabbitmqctl("list_queues", "name", "durable", "arguments")).anySatisfy(line -> assertThat(line)
			.startsWith("cart.checkouts\ttrue\t")
			.contains("x-dead-letter-exchange", "kitapsepeti.dlx", "x-dead-letter-routing-key", "cart.checkouts.dead"))
			.anySatisfy(line -> assertThat(line).startsWith("cart.checkouts.dlq\ttrue\t"));
		assertThat(rabbitmqctl("list_exchanges", "name", "type", "durable"))
			.contains("kitapsepeti.events\ttopic\ttrue", "kitapsepeti.dlx\tdirect\ttrue");
		assertThat(rabbitmqctl("list_bindings", "source_name", "destination_name", "routing_key"))
			.contains("kitapsepeti.events\tcart.checkouts\tcart.checked-out",
					"kitapsepeti.dlx\tcart.checkouts.dlq\tcart.checkouts.dead");

		SimpleMessageListenerContainer container = (SimpleMessageListenerContainer) this.listenerRegistry
			.getListenerContainer(CartCheckoutsConsumerConfig.LISTENER_ID);
		assertThat(container).isNotNull();
		assertThat(container.getActiveConsumerCount()).isEqualTo(1);
		assertThat(ReflectionTestUtils.getField(container, "prefetchCount")).isEqualTo(10);
	}

	@Test
	void consumerLogsContainNoIds(CapturedOutput output) {
		UUID cartId = activeCartWithOneLine();
		UUID eventId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();

		publish(message("CartCheckedOut", """
				{"eventId":"%s","eventVersion":1,"cartId":"%s","userId":"%s","orderId":"%s",\
				"occurredAt":"2026-10-05T07:00:00Z"}""".formatted(eventId, cartId, this.userId, orderId)));
		awaitStatus(cartId, "checked_out");
		publish(checkedOut(UUID.randomUUID(), this.userId));
		await().atMost(TIMEOUT).untilAsserted(() ->
			assertThat(messageCount(CartCheckoutsConsumerConfig.DEAD_LETTER_QUEUE)).isEqualTo(1));

		assertThat(output.getOut().lines().filter(line -> line.contains("Cart checkout -> ")))
			.anySatisfy(line -> assertThat(line).contains("Cart checkout -> CHECKED_OUT (type=CartCheckedOut, durationMs="))
			.anySatisfy(line -> assertThat(line).contains("Cart checkout -> DLQ_CART_NOT_FOUND (type=CartCheckedOut"));
		assertThat(output.getAll()).doesNotContain(cartId.toString())
			.doesNotContain(this.userId.toString())
			.doesNotContain(eventId.toString())
			.doesNotContain(orderId.toString())
			.doesNotContain("Caused by")
			.doesNotContain("\tat ");
	}

	private UUID activeCartWithOneLine() {
		Cart cart = Cart.openFor(this.userId, clock);
		cart.addItem(UUID.randomUUID(), 2, new BigDecimal("10.00"), "TRY", "Sepetteki", null, clock);
		return carts.saveAndFlush(cart).getId();
	}

	private Message checkedOut(UUID cartId, UUID userId) {
		return message("CartCheckedOut", body(cartId, userId, 1));
	}

	private static String body(UUID cartId, UUID userId, int version) {
		return """
				{"eventId":"%s","eventVersion":%d,"cartId":"%s","userId":"%s","orderId":"%s",\
				"occurredAt":"2026-10-05T07:00:00Z"}""".formatted(UUID.randomUUID(), version, cartId, userId,
				UUID.randomUUID());
	}

	private static Message message(String type, String json) {
		return MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
			.setContentType(MessageProperties.CONTENT_TYPE_JSON)
			.setContentEncoding(StandardCharsets.UTF_8.name())
			.setType(type)
			.setMessageId(UUID.randomUUID().toString())
			.build();
	}

	private void publish(Message message) {
		this.rabbit.send(this.eventsExchange.getName(), CartCheckoutsConsumerConfig.ROUTING_KEY, message);
	}

	private List<String> rabbitmqctl(String command, String... columns) throws Exception {
		String[] args = Stream.concat(Stream.of("rabbitmqctl", "-q", command, "--no-table-headers"), Stream.of(columns))
			.toArray(String[]::new);
		ExecResult result = this.broker.execInContainer(args);
		assertThat(result.getExitCode()).isZero();
		return result.getStdout().lines().toList();
	}

	private void awaitStatus(UUID cartId, String expected) {
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(cartStatus(cartId)).isEqualTo(expected));
	}

	private long messageCount(String queue) {
		QueueInformation info = this.admin.getQueueInfo(queue);
		return info == null ? -1 : info.getMessageCount();
	}

	private String cartStatus(UUID cartId) {
		return jdbc.queryForObject("SELECT status FROM carts WHERE id = UUID_TO_BIN(?)", String.class,
				cartId.toString());
	}

	private String updatedAt(UUID cartId) {
		return jdbc.queryForObject("""
				SELECT DATE_FORMAT(updated_at, '%Y-%m-%dT%H:%i:%s.%fZ') FROM carts WHERE id = UUID_TO_BIN(?)""",
				String.class, cartId.toString());
	}

	private int itemCount(UUID cartId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE cart_id = UUID_TO_BIN(?)", Integer.class,
				cartId.toString());
	}

}
