package com.kitapsepeti.common.outbox;

import java.util.Map;

/**
 * Outbox {@code event_type} → topic exchange routing key. Eşlemeyi servis tanımlar (kendi olay tipleri);
 * routing key'ler sözleşmenin parçasıdır, değiştirmek kırıcı değişikliktir.
 */
@FunctionalInterface
public interface OutboxRoutingKeys {

	/** @throws IllegalStateException olay tipi için routing key tanımlı değilse */
	String forEventType(String eventType);

	/** Sabit eşleme; tanımsız tip {@code IllegalStateException("No routing key for event type <tip>")}. */
	static OutboxRoutingKeys of(Map<String, String> routingKeys) {
		Map<String, String> copy = Map.copyOf(routingKeys);
		return eventType -> {
			String routingKey = copy.get(eventType);
			if (routingKey == null) {
				throw new IllegalStateException("No routing key for event type " + eventType);
			}
			return routingKey;
		};
	}

}
