package com.kitapsepeti.payment.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class EventRoutingKeysTest {

	@Test
	void paymentEventsMapToPaymentKeys() {
		assertThat(EventRoutingKeys.forEventType("PaymentSucceeded")).isEqualTo("payment.succeeded");
		assertThat(EventRoutingKeys.forEventType("PaymentFailed")).isEqualTo("payment.failed");
	}

	@Test
	void unknownEventTypeFails() {
		assertThatIllegalStateException().isThrownBy(() -> EventRoutingKeys.forEventType("PaymentRefunded"))
			.withMessageContaining("PaymentRefunded");
	}

}
