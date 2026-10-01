package com.kitapsepeti.catalog.outbox;

import java.util.Map;

import com.kitapsepeti.catalog.service.event.BookRemovedEvent;
import com.kitapsepeti.catalog.service.event.BookUpsertedEvent;

/**
 * Outbox {@code event_type} → topic exchange routing key. Routing key'ler sözleşmenin parçasıdır
 * (bkz. {@code docs/events/}); consumer'lar bunlara bind eder, değiştirmek kırıcı değişikliktir.
 */
public final class EventRoutingKeys {

	private static final Map<String, String> ROUTING_KEYS = Map.of(
			BookUpsertedEvent.TYPE, "book.upserted",
			BookRemovedEvent.TYPE, "book.removed");

	private EventRoutingKeys() {
	}

	/** @throws IllegalStateException olay tipi için routing key tanımlı değilse */
	public static String forEventType(String eventType) {
		String routingKey = ROUTING_KEYS.get(eventType);
		if (routingKey == null) {
			throw new IllegalStateException("No routing key for event type " + eventType);
		}
		return routingKey;
	}

}
