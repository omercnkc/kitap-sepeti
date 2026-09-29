package com.kitapsepeti.user.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.kitapsepeti.user.entity.OutboxEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Tek bir outbox satırını exchange'e yayınlar ve broker onayını (publisher confirm) bekler.
 * Metot normal dönerse mesaj broker'ın sorumluluğundadır (persistent mesaj + durable exchange).
 * Onay: exchange'e kabul edildi demektir; bağlı kuyruk yoksa broker mesajı yine onaylar ve düşürür.
 */
@Component
public class OutboxPublisher {

	public static final String AGGREGATE_TYPE_HEADER = "aggregateType";

	public static final String AGGREGATE_ID_HEADER = "aggregateId";

	private final RabbitTemplate rabbitTemplate;

	private final OutboxProperties properties;

	public OutboxPublisher(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
		this.rabbitTemplate = rabbitTemplate;
		this.properties = properties;
	}

	/**
	 * @throws OutboxPublishException broker nack verirse, onay süresinde gelmezse veya bağlantı yoksa
	 * @throws IllegalStateException  olay tipi için routing key tanımlı değilse
	 */
	public void publish(OutboxEvent event) {
		String routingKey = EventRoutingKeys.forEventType(event.getEventType());
		CorrelationData correlation = new CorrelationData(event.getId().toString());
		try {
			rabbitTemplate.send(properties.exchange(), routingKey, toMessage(event), correlation);
		}
		catch (RuntimeException ex) {
			throw new OutboxPublishException("Send failed", ex);
		}
		CorrelationData.Confirm confirm = awaitConfirm(correlation, properties.confirmTimeout());
		if (!confirm.ack()) {
			throw new OutboxPublishException("Broker nacked message: " + confirm.reason());
		}
	}

	private static Message toMessage(OutboxEvent event) {
		return MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
			.setMessageId(event.getId().toString())
			.setType(event.getEventType())
			.setContentType(MessageProperties.CONTENT_TYPE_JSON)
			.setContentEncoding(StandardCharsets.UTF_8.name())
			.setTimestamp(Date.from(event.getCreatedAt()))
			.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
			.setHeader(AGGREGATE_TYPE_HEADER, event.getAggregateType())
			.setHeader(AGGREGATE_ID_HEADER, event.getAggregateId().toString())
			.build();
	}

	private static CorrelationData.Confirm awaitConfirm(CorrelationData correlation, Duration timeout) {
		try {
			return correlation.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new OutboxPublishException("Interrupted while waiting for broker confirm", ex);
		}
		catch (TimeoutException ex) {
			throw new OutboxPublishException("No broker confirm within " + timeout, ex);
		}
		catch (ExecutionException ex) {
			throw new OutboxPublishException("Broker confirm failed", ex.getCause());
		}
	}

}
