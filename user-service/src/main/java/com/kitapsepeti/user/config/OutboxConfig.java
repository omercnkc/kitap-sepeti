package com.kitapsepeti.user.config;

import com.kitapsepeti.common.outbox.OutboxConfiguration;
import com.kitapsepeti.common.outbox.OutboxProperties;
import com.kitapsepeti.user.outbox.OutboxPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Ortak outbox (exchange, relay, {@code OutboxService}) + user-service routing key eşlemesi. */
@Configuration(proxyBeanMethods = false)
@Import(OutboxConfiguration.class)
public class OutboxConfig {

	@Bean
	public OutboxPublisher outboxPublisher(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
		return new OutboxPublisher(rabbitTemplate, properties);
	}

}
