package com.kitapsepeti.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OutboxRoutingKeysTest {

	@Test
	void mapsKnownTypesAndRejectsUnknownWithTheExistingMessage() {
		Map<String, String> source = new HashMap<>(Map.of("BookUpserted", "book.upserted"));
		OutboxRoutingKeys keys = OutboxRoutingKeys.of(source);
		source.put("BookRemoved", "book.removed");

		assertThat(keys.forEventType("BookUpserted")).isEqualTo("book.upserted");
		assertThatIllegalStateException().isThrownBy(() -> keys.forEventType("BookRemoved"))
			.withMessage("No routing key for event type BookRemoved");
	}

}
