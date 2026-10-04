package com.kitapsepeti.catalog.config;

import com.kitapsepeti.catalog.outbox.OutboxPublisher;
import com.kitapsepeti.common.outbox.OutboxConfiguration;
import com.kitapsepeti.common.outbox.OutboxProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Ortak outbox (exchange, relay, {@code OutboxService}) + catalog-service routing key eşlemesi. */
@Configuration(proxyBeanMethods = false)
@Import(OutboxConfiguration.class)
public class OutboxConfig {

	@Bean
	public OutboxPublisher outboxPublisher(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
		return new OutboxPublisher(rabbitTemplate, properties);
	}

}
