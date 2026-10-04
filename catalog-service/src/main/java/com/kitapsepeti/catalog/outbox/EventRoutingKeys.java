package com.kitapsepeti.catalog.outbox;

import java.util.Map;

import com.kitapsepeti.catalog.service.event.BookRemovedEvent;
import com.kitapsepeti.catalog.service.event.BookUpsertedEvent;
import com.kitapsepeti.common.outbox.OutboxRoutingKeys;

/**
 * Outbox {@code event_type} → topic exchange routing key. Routing key'ler sözleşmenin parçasıdır
 * (bkz. {@code docs/events/}); consumer'lar bunlara bind eder, değiştirmek kırıcı değişikliktir.
 */
public final class EventRoutingKeys {

	private static final OutboxRoutingKeys ROUTING_KEYS = OutboxRoutingKeys.of(Map.of(
			BookUpsertedEvent.TYPE, "book.upserted",
			BookRemovedEvent.TYPE, "book.removed"));

	private EventRoutingKeys() {
	}

	/** @throws IllegalStateException olay tipi için routing key tanımlı değilse */
	public static String forEventType(String eventType) {
		return ROUTING_KEYS.forEventType(eventType);
	}

}
