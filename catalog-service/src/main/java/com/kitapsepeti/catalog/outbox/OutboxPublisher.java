package com.kitapsepeti.catalog.outbox;

import com.kitapsepeti.common.outbox.OutboxProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Ortak yayıncıyı catalog-service routing key eşlemesine ({@link EventRoutingKeys}) bağlar. */
public class OutboxPublisher extends com.kitapsepeti.common.outbox.OutboxPublisher {

	public OutboxPublisher(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
		super(rabbitTemplate, properties, EventRoutingKeys::forEventType);
	}

}
