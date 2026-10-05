package com.kitapsepeti.cart.config;

import com.kitapsepeti.common.amqp.EventsExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ortak olay exchange'i. Cart yalnızca tüketir (outbox'ı yok); tanım yayıncılarla aynı olsun diye common'dan gelir.
 * Broker'a RabbitAdmin ilk bağlantıda (listener container açılırken) declare eder.
 */
@Configuration(proxyBeanMethods = false)
public class EventsExchangeConfig {

	@Bean
	TopicExchange eventsExchange(@Value("${app.events.exchange}") String name) {
		return EventsExchange.create(name);
	}

}
