package com.kitapsepeti.order.outbox;

import java.util.Map;

import com.kitapsepeti.common.outbox.OutboxRoutingKeys;

/**
 * Outbox {@code event_type} → topic exchange routing key. Routing key'ler sözleşmenin parçasıdır
 * (bkz. {@code docs/events/}); consumer'lar bunlara bind eder, değiştirmek kırıcı değişikliktir.
 * Henüz olay yok: sipariş olayları (OrderPaid, OrderFailed, CartCheckedOut) sonraki adımlarda eklenecek.
 */
public final class EventRoutingKeys {

	private static final OutboxRoutingKeys ROUTING_KEYS = OutboxRoutingKeys.of(Map.of());

	private EventRoutingKeys() {
	}

	/** @throws IllegalStateException olay tipi için routing key tanımlı değilse */
	public static String forEventType(String eventType) {
		return ROUTING_KEYS.forEventType(eventType);
	}

}
