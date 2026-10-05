package com.kitapsepeti.common.outbox;

import java.time.Clock;

import com.kitapsepeti.common.amqp.EventsExchange;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox'ın ortak bean'leri; servis {@code @Import(OutboxConfiguration.class)} ile açıkça alır (auto-config yok).
 * Servisin ayrıca sağlaması gerekenler: kendi routing key eşlemesiyle bir {@link OutboxPublisher} bean'i,
 * {@code Clock} bean'i, {@code @EnableScheduling} (relay açıkken) ve ana sınıfta
 * {@code @AutoConfigurationPackage(basePackageClasses = { <Uygulama>.class, OutboxEvent.class })} (entity + repository
 * taraması; uygulama sınıfı da yazılmalı, doğrudan konan anotasyon varsayılan paketin yerine geçer).
 * <p>
 * RabbitMQ topolojisinin yayıncı tarafı: yalnızca olayların gönderildiği exchange. Kuyruk ve binding tanımlanmaz;
 * her consumer kendi kuyruğunu declare edip bind eder. Mesaj gövdesi outbox'taki hazır JSON olduğu için mesaj
 * dönüştürücü (JSON converter) yok.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

	private static final Logger log = LoggerFactory.getLogger(OutboxConfiguration.class);

	/** Tüm servisler aynı exchange'e yayınlar; tanım {@link EventsExchange}'te. */
	@Bean
	public TopicExchange eventsExchange(OutboxProperties properties) {
		return EventsExchange.create(properties.exchange());
	}

	@Bean
	public OutboxService outboxService(EntityManager entityManager, JsonMapper jsonMapper) {
		return new OutboxService(entityManager, jsonMapper);
	}

	@Bean
	@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
	public OutboxRelay outboxRelay(OutboxRepository outboxRepository, OutboxPublisher publisher,
			OutboxProperties properties, PlatformTransactionManager transactionManager, Clock clock) {
		return new OutboxRelay(outboxRepository, publisher, properties, transactionManager, clock);
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
