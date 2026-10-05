package com.kitapsepeti.common.amqp;

import org.springframework.amqp.core.TopicExchange;

/**
 * Tüm servislerin olay yayınladığı/tükettiği ortak topic exchange'in TEK tanımı. Durable, auto-delete değil,
 * argümansız: broker yeniden başlasa da, hiç kuyruk bağlı olmasa da kalır. Farklı tanımla declare broker'da
 * PRECONDITION_FAILED ile kanalı kapatır; bu yüzden yayıncı ({@code OutboxConfiguration}) ve yalnızca tüketen servis
 * exchange'i buradan kurar.
 */
public final class EventsExchange {

	private EventsExchange() {
	}

	public static TopicExchange create(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("exchange name must not be blank");
		}
		return new TopicExchange(name, true, false);
	}

}
