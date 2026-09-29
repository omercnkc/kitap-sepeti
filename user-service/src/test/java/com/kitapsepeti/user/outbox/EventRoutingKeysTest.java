package com.kitapsepeti.user.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class EventRoutingKeysTest {

	@Test
	void userRegisteredMapsToUserRegisteredKey() {
		assertThat(EventRoutingKeys.forEventType("UserRegistered")).isEqualTo("user.registered");
	}

	@Test
	void unknownEventTypeFails() {
		assertThatIllegalStateException().isThrownBy(() -> EventRoutingKeys.forEventType("UserDeleted"))
			.withMessageContaining("UserDeleted");
	}

}
