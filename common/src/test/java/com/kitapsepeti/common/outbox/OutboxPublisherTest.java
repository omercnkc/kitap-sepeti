package com.kitapsepeti.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** Mesaj sözleşmesi (özellikler, başlıklar, gövde) ve onay/hata yolları; broker'sız. */
class OutboxPublisherTest {

	private static final OutboxProperties PROPERTIES = new OutboxProperties(true, "kitapsepeti.events",
			Duration.ofSeconds(2), 50, Duration.ofMillis(200));

	private static final OutboxRoutingKeys ROUTING_KEYS = OutboxRoutingKeys.of(Map.of("BookUpserted", "book.upserted"));

	private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

	private final OutboxPublisher publisher = new OutboxPublisher(this.rabbitTemplate, PROPERTIES, ROUTING_KEYS);

	@Test
	void sendsContractMessageToExchangeWithMappedRoutingKey() {
		OutboxEvent event = event("BookUpserted", "{\"title\":\"Kürk Mantolu Madonna\"}");
		confirmWith(true, null);

		this.publisher.publish(event);

		ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
		ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
		verify(this.rabbitTemplate).send(eq("kitapsepeti.events"), eq("book.upserted"), message.capture(),
				correlation.capture());
		MessageProperties properties = message.getValue().getMessageProperties();
		assertThat(properties.getMessageId()).isEqualTo(event.getId().toString());
		assertThat(correlation.getValue().getId()).isEqualTo(event.getId().toString());
		assertThat(properties.getType()).isEqualTo("BookUpserted");
		assertThat(properties.getContentType()).isEqualTo("application/json");
		assertThat(properties.getContentEncoding()).isEqualTo("UTF-8");
		assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		assertThat(properties.getTimestamp().toInstant()).isEqualTo(event.getCreatedAt());
		assertThat(properties.<Object>getHeader(OutboxPublisher.AGGREGATE_TYPE_HEADER)).isEqualTo("book");
		assertThat(properties.<Object>getHeader(OutboxPublisher.AGGREGATE_ID_HEADER))
			.isEqualTo(event.getAggregateId().toString());
		assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo(event.getPayload());
	}

	@Test
	void unknownEventTypeFailsBeforeSending() {
		assertThatIllegalStateException().isThrownBy(() -> this.publisher.publish(event("BookDeleted", "{}")))
			.withMessage("No routing key for event type BookDeleted");
		verifyNoInteractions(this.rabbitTemplate);
	}

	@Test
	void nackBecomesPublishException() {
		confirmWith(false, "queue limit");

		assertThatThrownBy(() -> this.publisher.publish(event("BookUpserted", "{}")))
			.isInstanceOf(OutboxPublishException.class)
			.hasMessage("Broker nacked message: queue limit");
	}

	@Test
	void missingConfirmTimesOut() {
		assertThatThrownBy(() -> this.publisher.publish(event("BookUpserted", "{}")))
			.isInstanceOf(OutboxPublishException.class)
			.hasMessage("No broker confirm within PT0.2S");
	}

	@Test
	void sendFailureBecomesPublishExceptionWithCause() {
		AmqpConnectException down = new AmqpConnectException(new java.net.ConnectException("refused"));
		doThrow(down).when(this.rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

		assertThatThrownBy(() -> this.publisher.publish(event("BookUpserted", "{}")))
			.isInstanceOf(OutboxPublishException.class)
			.hasMessage("Send failed")
			.hasCause(down);
	}

	private void confirmWith(boolean ack, String reason) {
		doAnswer(invocation -> {
			CorrelationData correlation = invocation.getArgument(3);
			correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
			return null;
		}).when(this.rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
	}

	private static OutboxEvent event(String eventType, String payload) {
		OutboxEvent event = new OutboxEvent("book", UUID.randomUUID(), eventType, payload);
		ReflectionTestUtils.setField(event, "createdAt", Instant.parse("2026-01-01T10:00:00.123Z"));
		return event;
	}

}
