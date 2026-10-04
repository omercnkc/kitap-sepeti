package com.kitapsepeti.order.outbox;

import java.util.Map;

import com.kitapsepeti.common.outbox.OutboxRoutingKeys;
import com.kitapsepeti.order.service.event.CartCheckedOutEvent;
import com.kitapsepeti.order.service.event.OrderFailedEvent;
import com.kitapsepeti.order.service.event.OrderPaidEvent;

/**
 * Outbox {@code event_type} → topic exchange routing key. Routing key'ler sözleşmenin parçasıdır
 * (bkz. {@code docs/events/}); consumer'lar bunlara bind eder, değiştirmek kırıcı değişikliktir.
 */
public final class EventRoutingKeys {

	private static final OutboxRoutingKeys ROUTING_KEYS = OutboxRoutingKeys.of(Map.of(
			OrderPaidEvent.TYPE, "order.paid",
			OrderFailedEvent.TYPE, "order.failed",
			CartCheckedOutEvent.TYPE, "cart.checked-out"));

	private EventRoutingKeys() {
	}

	/** @throws IllegalStateException olay tipi için routing key tanımlı değilse */
	public static String forEventType(String eventType) {
		return ROUTING_KEYS.forEventType(eventType);
	}

}
