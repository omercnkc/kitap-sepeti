package com.kitapsepeti.payment.config;

import com.kitapsepeti.payment.outbox.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * RabbitMQ topolojisinin yayıncı tarafı: yalnızca olayların gönderildiği exchange.
 * Kuyruk ve binding tanımlanmaz; her consumer kendi kuyruğunu declare edip bind eder.
 * Mesaj gövdesi outbox'taki hazır JSON olduğu için mesaj dönüştürücü (JSON converter) yok.
 * <p>
 * Exchange user-service ve catalog-service ile paylaşılır ve tanımı (ad, tip, durable, autoDelete, argümanlar)
 * birebir aynı olmalıdır; farklı tanımla declare broker'da PRECONDITION_FAILED ile kanalı kapatır.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxProperties.class)
public class RabbitConfig {

	private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);

	/** Durable, auto-delete değil: broker yeniden başlasa da, hiç kuyruk bağlı olmasa da kalır. */
	@Bean
	public TopicExchange eventsExchange(OutboxProperties properties) {
		return new TopicExchange(properties.exchange(), true, false);
	}

	/**
	 * RabbitAdmin exchange'i normalde ilk bağlantı açılınca declare eder; bu da ilk olay yayınlanana
	 * kadar olmaz. Açılışta denenir ki consumer'lar kuyruklarını hemen bind edebilsin. Broker kapalıysa
	 * uygulama yine açılır; declare, bağlantı kurulduğunda RabbitAdmin tarafından tekrarlanır.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void declareTopologyOnStartup(ApplicationReadyEvent event) {
		AmqpAdmin amqpAdmin = event.getApplicationContext().getBean(AmqpAdmin.class);
		try {
			amqpAdmin.initialize();
		}
		catch (AmqpException ex) {
			log.warn("RabbitMQ unavailable at startup; exchange will be declared on first connection ({})",
					ex.getClass().getSimpleName());
		}
	}

}
