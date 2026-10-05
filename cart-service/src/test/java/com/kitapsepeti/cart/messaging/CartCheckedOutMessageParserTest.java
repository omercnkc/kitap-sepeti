package com.kitapsepeti.cart.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.kitapsepeti.cart.messaging.PoisonMessageException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

class CartCheckedOutMessageParserTest {

	private final CartCheckedOutMessageParser parser = new CartCheckedOutMessageParser(JsonMapper.builder().build());

	private final UUID eventId = UUID.randomUUID();

	private final UUID cartId = UUID.randomUUID();

	private final UUID userId = UUID.randomUUID();

	private final UUID orderId = UUID.randomUUID();

	@Test
	void readsAllFieldsAndIgnoresUnknownOnes() {
		CartCheckedOutMessage parsed = parser.parse(message("CartCheckedOut", """
				{"eventId":"%s","eventVersion":1,"cartId":"%s","userId":"%s","orderId":"%s",\
				"occurredAt":"2026-10-05T07:00:00.5Z","extra":true}""".formatted(eventId, cartId, userId, orderId)));

		assertThat(parsed).isEqualTo(new CartCheckedOutMessage(eventId, cartId, userId, orderId,
				Instant.parse("2026-10-05T07:00:00.5Z")));
	}

	@Test
	void unknownOrMissingTypeIsPoisonBeforeBodyIsRead() {
		assertPoison(message("OrderPaid", "{"), Reason.UNKNOWN_TYPE);
		assertPoison(message(null, valid()), Reason.UNKNOWN_TYPE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{", "[]", "\"text\"", "null", "" })
	void malformedOrNonObjectBodyIsPoison(String body) {
		assertPoison(message("CartCheckedOut", body), Reason.MALFORMED_JSON);
	}

	@Test
	void versionMustBeIntegerOne() {
		assertPoison(message("CartCheckedOut", valid().replace("\"eventVersion\":1", "\"eventVersion\":2")),
				Reason.UNSUPPORTED_VERSION);
		assertPoison(message("CartCheckedOut", valid().replace("\"eventVersion\":1", "\"eventVersion\":\"1\"")),
				Reason.INVALID_FIELD);
		assertPoison(message("CartCheckedOut", valid().replace("\"eventVersion\":1,", "")), Reason.MISSING_FIELD);
	}

	@ParameterizedTest
	@ValueSource(strings = { "eventId", "cartId", "userId", "orderId", "occurredAt" })
	void everyFieldIsRequired(String field) {
		String withoutField = valid().replaceAll("\"" + field + "\":\"[^\"]*\",?", "").replace(",}", "}");
		assertPoison(message("CartCheckedOut", withoutField), Reason.MISSING_FIELD);
		String nullField = valid().replaceAll("\"" + field + "\":\"[^\"]*\"", "\"" + field + "\":null");
		assertPoison(message("CartCheckedOut", nullField), Reason.MISSING_FIELD);
	}

	@Test
	void invalidUuidOrInstantIsPoison() {
		assertPoison(message("CartCheckedOut", valid().replace(cartId.toString(), "not-a-uuid")), Reason.INVALID_FIELD);
		assertPoison(message("CartCheckedOut", valid().replace("2026-10-05T07:00:00Z", "yesterday")),
				Reason.INVALID_FIELD);
		assertPoison(message("CartCheckedOut", valid().replace("\"" + userId + "\"", "42")), Reason.INVALID_FIELD);
	}

	@Test
	void poisonExceptionCarriesNoIds() {
		assertThatThrownBy(() -> parser.parse(message("CartCheckedOut", valid().replace(cartId.toString(), "x"))))
			.hasMessage("INVALID_FIELD")
			.hasNoCause();
	}

	private String valid() {
		return """
				{"eventId":"%s","eventVersion":1,"cartId":"%s","userId":"%s","orderId":"%s",\
				"occurredAt":"2026-10-05T07:00:00Z"}""".formatted(eventId, cartId, userId, orderId);
	}

	private void assertPoison(Message message, Reason reason) {
		assertThatThrownBy(() -> parser.parse(message)).isInstanceOfSatisfying(PoisonMessageException.class,
				ex -> assertThat(ex.reason()).isEqualTo(reason));
	}

	private static Message message(String type, String json) {
		MessageBuilder builder = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8));
		if (type != null) {
			builder.setType(type);
		}
		return builder.build();
	}

}
