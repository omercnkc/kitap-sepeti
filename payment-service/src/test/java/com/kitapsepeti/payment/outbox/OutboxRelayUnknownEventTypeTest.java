package com.kitapsepeti.payment.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.common.outbox.OutboxEvent;
import com.kitapsepeti.common.outbox.OutboxProperties;
import com.kitapsepeti.common.outbox.OutboxRelay;
import com.kitapsepeti.common.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Routing key'i tanımsız olay tipi catalog/user-service'teki gibi davranır: satır yayınlanmaz, tur durur ve sonraki
 * satırlar da bekler (kuyruk bloklanır). Spring context'siz.
 */
@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayUnknownEventTypeTest {

	private static final OutboxProperties PROPERTIES = new OutboxProperties(true, "kitapsepeti.events",
			Duration.ofSeconds(2), 50, Duration.ofSeconds(5));

	@Test
	void unknownEventTypeBlocksTheQueueWithWarn(CapturedOutput output) {
		OutboxEvent unknown = new OutboxEvent(UUID.randomUUID(), "payment", UUID.randomUUID(), "PaymentRefunded", "{}");
		OutboxEvent next = new OutboxEvent(UUID.randomUUID(), "payment", UUID.randomUUID(), "PaymentSucceeded", "{}");
		OutboxRepository repository = mock(OutboxRepository.class);
		when(repository.lockUnpublishedBatch(anyInt())).thenReturn(List.of(unknown, next));
		RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
		OutboxRelay relay = new OutboxRelay(repository, new OutboxPublisher(rabbitTemplate, PROPERTIES), PROPERTIES,
				new NoOpTransactionManager(), Clock.systemUTC());

		relay.poll();

		assertThat(unknown.getPublishedAt()).isNull();
		assertThat(next.getPublishedAt()).isNull();
		verifyNoInteractions(rabbitTemplate);
		assertThat(output).contains("Outbox publish failed, will retry on next poll")
			.contains("eventType=PaymentRefunded, error=IllegalStateException: No routing key for event type PaymentRefunded")
			.doesNotContain("\tat ");
	}

	private static final class NoOpTransactionManager implements PlatformTransactionManager {

		@Override
		public TransactionStatus getTransaction(TransactionDefinition definition) {
			return new SimpleTransactionStatus();
		}

		@Override
		public void commit(TransactionStatus status) {
		}

		@Override
		public void rollback(TransactionStatus status) {
		}

	}

}
