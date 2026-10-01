package com.kitapsepeti.catalog.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class EventRoutingKeysTest {

	@Test
	void bookEventsMapToBookKeys() {
		assertThat(EventRoutingKeys.forEventType("BookUpserted")).isEqualTo("book.upserted");
		assertThat(EventRoutingKeys.forEventType("BookRemoved")).isEqualTo("book.removed");
	}

	@Test
	void unknownEventTypeFails() {
		assertThatIllegalStateException().isThrownBy(() -> EventRoutingKeys.forEventType("BookDeleted"))
			.withMessageContaining("BookDeleted");
	}

}
