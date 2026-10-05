package com.kitapsepeti.cart.messaging;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import com.kitapsepeti.cart.messaging.PoisonMessageException.Reason;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** CartCheckedOut olayını ham AMQP mesajından katı zorunlu alanlarla okur; bilinmeyen JSON alanlarını yok sayar. */
@Component
class CartCheckedOutMessageParser {

	static final String TYPE = "CartCheckedOut";

	private static final int VERSION = 1;

	private final JsonMapper jsonMapper;

	CartCheckedOutMessageParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	CartCheckedOutMessage parse(Message message) {
		if (!TYPE.equals(message.getMessageProperties().getType())) {
			throw new PoisonMessageException(Reason.UNKNOWN_TYPE);
		}
		JsonNode root;
		try {
			root = this.jsonMapper.readTree(message.getBody());
		}
		catch (RuntimeException ex) {
			throw new PoisonMessageException(Reason.MALFORMED_JSON);
		}
		if (root == null || !root.isObject()) {
			throw new PoisonMessageException(Reason.MALFORMED_JSON);
		}
		int version = requiredInt(root, "eventVersion");
		if (version != VERSION) {
			throw new PoisonMessageException(Reason.UNSUPPORTED_VERSION);
		}
		return new CartCheckedOutMessage(requiredUuid(root, "eventId"), requiredUuid(root, "cartId"),
				requiredUuid(root, "userId"), requiredUuid(root, "orderId"), requiredInstant(root, "occurredAt"));
	}

	private static JsonNode required(JsonNode root, String field) {
		JsonNode value = root.get(field);
		if (value == null || value.isNull()) {
			throw new PoisonMessageException(Reason.MISSING_FIELD);
		}
		return value;
	}

	private static String requiredText(JsonNode root, String field) {
		JsonNode value = required(root, field);
		if (!value.isTextual() || value.asText().isBlank()) {
			throw new PoisonMessageException(Reason.INVALID_FIELD);
		}
		return value.asText();
	}

	private static int requiredInt(JsonNode root, String field) {
		JsonNode value = required(root, field);
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw new PoisonMessageException(Reason.INVALID_FIELD);
		}
		return value.intValue();
	}

	private static UUID requiredUuid(JsonNode root, String field) {
		try {
			return UUID.fromString(requiredText(root, field));
		}
		catch (IllegalArgumentException ex) {
			throw new PoisonMessageException(Reason.INVALID_FIELD);
		}
	}

	private static Instant requiredInstant(JsonNode root, String field) {
		try {
			return Instant.parse(requiredText(root, field));
		}
		catch (DateTimeParseException ex) {
			throw new PoisonMessageException(Reason.INVALID_FIELD);
		}
	}

}
