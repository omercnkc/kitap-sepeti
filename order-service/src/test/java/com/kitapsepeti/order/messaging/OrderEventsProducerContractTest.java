package com.kitapsepeti.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.kitapsepeti.order.service.event.CartCheckedOutEvent;
import com.kitapsepeti.order.service.event.OrderFailedEvent;
import com.kitapsepeti.order.service.event.OrderPaidEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Üretici sözleşme testi: Order'ın yayınladığı her olay için gerçek Java record'u Boot JsonMapper ile serileştirilir
 * ve belgedeki ({@code docs/events/}) örnek JSON ile aynı alan kümesine ve alan tiplerine sahip olduğu doğrulanır.
 */
class OrderEventsProducerContractTest {

	private static final Path EVENTS_DIR = Path.of("..", "docs", "events");

	private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*\\n([\\s\\S]*?)\\n```");

	private static final JsonMapper jsonMapper = JsonMapper.builder().build();

	static Stream<Arguments> producedEvents() {
		return Stream.of(
				Arguments.of("order-paid.md", new OrderPaidEvent(UUID.randomUUID(), OrderPaidEvent.VERSION,
						UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "149.90", "TRY", 2, Instant.now())),
				Arguments.of("order-failed.md", new OrderFailedEvent(UUID.randomUUID(), OrderFailedEvent.VERSION,
						UUID.randomUUID(), UUID.randomUUID(), "CARD_DECLINED", Instant.now())),
				Arguments.of("cart-checked-out.md", new CartCheckedOutEvent(UUID.randomUUID(), CartCheckedOutEvent.VERSION,
						UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now())));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("producedEvents")
	void producedEventMatchesDocExampleFieldsAndTypes(String docName, Object eventRecord) throws Exception {
		Path docPath = EVENTS_DIR.resolve(docName);
		assertThat(docPath).as("olay belgesi").exists();

		String exampleJson = extractJsonExample(docPath);
		JsonNode docTree = jsonMapper.readTree(exampleJson);
		JsonNode recordTree = jsonMapper.readTree(jsonMapper.writeValueAsString(eventRecord));

		Set<String> docFields = new TreeSet<>(docTree.propertyNames());
		Set<String> recordFields = new TreeSet<>(recordTree.propertyNames());

		assertThat(recordFields).as("%s alan adları", docName).isEqualTo(docFields);

		for (String field : docFields) {
			JsonNode docValue = docTree.get(field);
			JsonNode recordValue = recordTree.get(field);
			assertThat(recordValue.getNodeType())
				.as("%s alanının JSON tipi (%s)", field, docName)
				.isEqualTo(docValue.getNodeType());
		}
	}

	private static String extractJsonExample(Path docPath) throws IOException {
		String content = Files.readString(docPath, StandardCharsets.UTF_8);
		Matcher matcher = JSON_BLOCK.matcher(content);
		if (!matcher.find()) {
			throw new IllegalArgumentException("No ```json block found in " + docPath);
		}
		return matcher.group(1).trim();
	}

}
