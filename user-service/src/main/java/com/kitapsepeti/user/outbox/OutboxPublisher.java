package com.kitapsepeti.user.outbox;

import com.kitapsepeti.common.outbox.OutboxProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Ortak yayıncıyı user-service routing key eşlemesine ({@link EventRoutingKeys}) bağlar. */
public class OutboxPublisher extends com.kitapsepeti.common.outbox.OutboxPublisher {

	public OutboxPublisher(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
		super(rabbitTemplate, properties, EventRoutingKeys::forEventType);
	}

}
