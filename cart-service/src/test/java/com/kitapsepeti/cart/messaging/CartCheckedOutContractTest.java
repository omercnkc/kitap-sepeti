package com.kitapsepeti.cart.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kitapsepeti.cart.config.CartCheckoutsConsumerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tüketici sözleşmesi: {@code docs/events/cart-checked-out.md} belgesindeki örnek JSON ve routing key
 * Cart'ın tükettiği sözleşmeyle doğrulanır. Order koduna doğrudan derleme bağımlılığı yoktur.
 */
class CartCheckedOutContractTest {

	private static final Path EVENT_DOC = Path.of("..", "docs", "events", "cart-checked-out.md");

	private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*\\n([\\s\\S]*?)\\n```");

	private static final Pattern ROUTING_KEY_ROW = Pattern.compile("\\|\\s*Routing key\\s*\\|\\s*`?([^`\\|\\s]+)`?\\s*\\|");

	private static JsonMapper jsonMapper;

	@BeforeAll
	static void loadBootMapper() {
		AtomicReference<JsonMapper> mapper = new AtomicReference<>();
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
			.run(context -> mapper.set(context.getBean(JsonMapper.class)));
		jsonMapper = mapper.get();
	}

	@Test
	void docExamplePayloadIsReadableByCartParser() throws Exception {
		assertThat(EVENT_DOC).as("olay belgesi").exists();
		String json = extractJsonExample(EVENT_DOC);

		Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
			.setType(CartCheckedOutMessageParser.TYPE)
			.build();

		CartCheckedOutMessage parsed = new CartCheckedOutMessageParser(jsonMapper).parse(message);

		JsonNode root = jsonMapper.readTree(json);
		assertThat(parsed.eventId()).isEqualTo(UUID.fromString(root.get("eventId").asText()));
		assertThat(parsed.cartId()).isEqualTo(UUID.fromString(root.get("cartId").asText()));
		assertThat(parsed.userId()).isEqualTo(UUID.fromString(root.get("userId").asText()));
		assertThat(parsed.orderId()).isEqualTo(UUID.fromString(root.get("orderId").asText()));
		assertThat(parsed.occurredAt()).isEqualTo(Instant.parse(root.get("occurredAt").asText()));
	}

	@Test
	void routingKeyMatchesConsumerConfig() throws Exception {
		assertThat(EVENT_DOC).as("olay belgesi").exists();
		String routingKey = extractRoutingKey(EVENT_DOC);
		assertThat(routingKey).isEqualTo(CartCheckoutsConsumerConfig.ROUTING_KEY);
	}

	@Test
	void docPayloadFieldsMatchConsumedFields() throws Exception {
		String json = extractJsonExample(EVENT_DOC);
		JsonNode root = jsonMapper.readTree(json);

		Set<String> fields = new TreeSet<>(root.propertyNames());

		assertThat(fields).containsExactly("cartId", "eventId", "eventVersion", "occurredAt", "orderId", "userId");
	}

	private static String extractJsonExample(Path docPath) throws IOException {
		String content = Files.readString(docPath, StandardCharsets.UTF_8);
		Matcher matcher = JSON_BLOCK.matcher(content);
		if (!matcher.find()) {
			throw new IllegalArgumentException("No ```json block found in " + docPath);
		}
		return matcher.group(1).trim();
	}

	private static String extractRoutingKey(Path docPath) throws IOException {
		String content = Files.readString(docPath, StandardCharsets.UTF_8);
		Matcher matcher = ROUTING_KEY_ROW.matcher(content);
		if (!matcher.find()) {
			throw new IllegalArgumentException("No Routing key row found in " + docPath);
		}
		return matcher.group(1).trim();
	}

}
