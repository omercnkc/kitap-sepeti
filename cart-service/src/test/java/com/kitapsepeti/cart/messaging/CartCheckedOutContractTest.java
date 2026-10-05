package com.kitapsepeti.cart.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.kitapsepeti.cart.config.CartCheckoutsConsumerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tüketici sözleşmesi: Order'ın {@code CartCheckedOutEvent} record'u (order-service kaynağından, değiştirilmeden
 * derlenir) Order'ın outbox'ının kullandığı Boot JsonMapper'ıyla serileştirilir ve Cart'ın parser'ı onu okur. Order
 * alan ekler/kaldırır/yeniden adlandırır ya da tip/sürüm/routing key değiştirirse bu test kırılır.
 */
class CartCheckedOutContractTest {

	private static final Path ORDER_EVENTS = Path.of("..", "order-service", "src", "main", "java", "com", "kitapsepeti",
			"order", "service", "event");

	private static final Path ORDER_ROUTING_KEYS = Path.of("..", "order-service", "src", "main", "java", "com",
			"kitapsepeti", "order", "outbox", "EventRoutingKeys.java");

	private static Class<?> orderEvent;

	private static JsonMapper jsonMapper;

	@BeforeAll
	static void compileOrderRecordAndLoadBootMapper(@TempDir Path classes) throws Exception {
		Path source = ORDER_EVENTS.resolve("CartCheckedOutEvent.java");
		assertThat(source).as("order-service kaynağı (testin çalışma dizini cart-service)").exists();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertThat(compiler.run(null, null, null, "-d", classes.toString(), source.toString())).isZero();
		URLClassLoader loader = new URLClassLoader(new URL[] { classes.toUri().toURL() },
				CartCheckedOutContractTest.class.getClassLoader());
		orderEvent = loader.loadClass("com.kitapsepeti.order.service.event.CartCheckedOutEvent");

		AtomicReference<JsonMapper> mapper = new AtomicReference<>();
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
			.run(context -> mapper.set(context.getBean(JsonMapper.class)));
		jsonMapper = mapper.get();
	}

	@Test
	void orderPayloadIsReadableByCartParser() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID cartId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		Instant occurredAt = Instant.parse("2026-10-05T07:00:00.123456Z");
		int version = orderEvent.getField("VERSION").getInt(null);
		Object event = newOrderEvent(eventId, version, cartId, userId, orderId, occurredAt);
		String json = jsonMapper.writeValueAsString(event);
		Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
			.setType((String) orderEvent.getField("TYPE").get(null))
			.build();

		CartCheckedOutMessage parsed = new CartCheckedOutMessageParser(jsonMapper).parse(message);

		assertThat(parsed).isEqualTo(new CartCheckedOutMessage(eventId, cartId, userId, orderId, occurredAt));
	}

	@Test
	void typeVersionAndRoutingKeyMatchOrder() throws Exception {
		assertThat(orderEvent.getField("TYPE").get(null)).isEqualTo(CartCheckedOutMessageParser.TYPE);
		assertThat(orderEvent.getField("VERSION").getInt(null)).isEqualTo(1);
		assertThat(Files.readString(ORDER_ROUTING_KEYS))
			.contains("CartCheckedOutEvent.TYPE, \"" + CartCheckoutsConsumerConfig.ROUTING_KEY + "\"");
	}

	/** Bilinen alanlar dışında Order payload'ında alan yok; varsa Cart onu bilinçli olarak görmezden mi geliyor, karar ver. */
	@Test
	void orderPayloadFieldsAreExactlyTheConsumedOnes() {
		assertThat(Arrays.stream(orderEvent.getRecordComponents()).map(RecordComponent::getName))
			.containsExactly("eventId", "eventVersion", "cartId", "userId", "orderId", "occurredAt");
	}

	private static Object newOrderEvent(UUID eventId, int version, UUID cartId, UUID userId, UUID orderId,
			Instant occurredAt) throws Exception {
		Class<?>[] types = Arrays.stream(orderEvent.getRecordComponents()).map(RecordComponent::getType)
			.toArray(Class<?>[]::new);
		return orderEvent.getDeclaredConstructor(types).newInstance(eventId, version, cartId, userId, orderId,
				occurredAt);
	}

}
