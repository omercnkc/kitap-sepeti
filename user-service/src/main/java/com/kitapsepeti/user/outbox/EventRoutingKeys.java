package com.kitapsepeti.user.outbox;

import java.util.Map;

import com.kitapsepeti.common.outbox.OutboxRoutingKeys;
import com.kitapsepeti.user.service.event.UserRegisteredEvent;

/**
 * Outbox {@code event_type} → topic exchange routing key. Routing key'ler sözleşmenin parçasıdır
 * (bkz. {@code docs/events/}); consumer'lar bunlara bind eder, değiştirmek kırıcı değişikliktir.
 */
public final class EventRoutingKeys {

	private static final OutboxRoutingKeys ROUTING_KEYS = OutboxRoutingKeys.of(Map.of(
			UserRegisteredEvent.TYPE, "user.registered"));

	private EventRoutingKeys() {
	}

	/** @throws IllegalStateException olay tipi için routing key tanımlı değilse */
	public static String forEventType(String eventType) {
		return ROUTING_KEYS.forEventType(eventType);
	}

}
