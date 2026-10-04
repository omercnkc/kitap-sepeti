package com.kitapsepeti.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Relay turu Spring context'siz: sıra, ilk hatada durma, tanımsız routing key, DB kesintisi. */
@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayTest {

	private static final OutboxProperties PROPERTIES = new OutboxProperties(true, "kitapsepeti.events",
			Duration.ofSeconds(2), 50, Duration.ofSeconds(5));

	private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

	private static final String SKIPPED = "Outbox poll skipped, database unavailable: ";

	private static final String DB_DETAIL = "Communications link failure to mysql:3306";

	private final OutboxRepository repository = mock(OutboxRepository.class);

	private final OutboxPublisher publisher = mock(OutboxPublisher.class);

	@Test
	void publishesInOrderAndMarksRowsWithClockTime() {
		OutboxEvent first = event("A");
		OutboxEvent second = event("B");
		when(this.repository.lockUnpublishedBatch(50)).thenReturn(List.of(first, second));

		relay(new StubTransactionManager(null)).poll();

		verify(this.publisher).publish(first);
		verify(this.publisher).publish(second);
		assertThat(first.getPublishedAt()).isEqualTo(NOW);
		assertThat(second.getPublishedAt()).isEqualTo(NOW);
	}

	@Test
	void firstFailureStopsTheBatchAndLeavesLaterRowsUnpublished(CapturedOutput output) {
		OutboxEvent first = event("A");
		OutboxEvent failing = event("B");
		OutboxEvent later = event("C");
		when(this.repository.lockUnpublishedBatch(anyInt())).thenReturn(List.of(first, failing, later));
		doThrow(new OutboxPublishException("Send failed", new IllegalStateException("socket closed")))
			.when(this.publisher).publish(failing);

		relay(new StubTransactionManager(null)).poll();

		assertThat(first.getPublishedAt()).isEqualTo(NOW);
		assertThat(failing.getPublishedAt()).isNull();
		assertThat(later.getPublishedAt()).isNull();
		verify(this.publisher, org.mockito.Mockito.never()).publish(later);
		assertThat(output).contains("Outbox publish failed, will retry on next poll: id=" + failing.getId()
				+ ", eventType=B, error=OutboxPublishException: Send failed (IllegalStateException)")
			.doesNotContain("socket closed")
			.doesNotContain("\tat ");
	}

	/** Mevcut davranış: tanımsız tip satırı yayınlanmaz, tur durur, sonraki satırlar da bekler (kuyruk tıkanır). */
	@Test
	void unknownRoutingKeyBlocksTheQueueWithoutSending(CapturedOutput output) {
		OutboxEvent unknown = event("Unknown");
		OutboxEvent next = event("Known");
		when(this.repository.lockUnpublishedBatch(anyInt())).thenReturn(List.of(unknown, next));
		RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
		OutboxPublisher realPublisher = new OutboxPublisher(rabbitTemplate, PROPERTIES,
				OutboxRoutingKeys.of(Map.of("Known", "x.known")));

		new OutboxRelay(this.repository, realPublisher, PROPERTIES, new StubTransactionManager(null), clock()).poll();

		assertThat(unknown.getPublishedAt()).isNull();
		assertThat(next.getPublishedAt()).isNull();
		verifyNoInteractions(rabbitTemplate);
		assertThat(output).contains("eventType=Unknown, error=IllegalStateException: No routing key for event type Unknown");
	}

	@Test
	void transactionCannotBeOpenedLogsSingleWarnWithoutStackTrace(CapturedOutput output) {
		RuntimeException failure = new CannotCreateTransactionException("Could not open JPA EntityManager: " + DB_DETAIL,
				new SQLException(DB_DETAIL));

		relay(new StubTransactionManager(failure)).poll();

		assertSingleWarn(output, "CannotCreateTransactionException");
		verifyNoInteractions(this.repository, this.publisher);
	}

	@Test
	void repositoryDataAccessFailureLogsSingleWarnWithoutStackTrace(CapturedOutput output) {
		when(this.repository.lockUnpublishedBatch(anyInt()))
			.thenThrow(new DataAccessResourceFailureException(DB_DETAIL, new SQLException(DB_DETAIL)));

		relay(new StubTransactionManager(null)).poll();

		assertSingleWarn(output, "DataAccessResourceFailureException");
		verifyNoInteractions(this.publisher);
	}

	@Test
	void unexpectedExceptionIsLeftToSchedulerErrorHandler(CapturedOutput output) {
		when(this.repository.lockUnpublishedBatch(anyInt())).thenThrow(new IllegalStateException("bug"));

		assertThatIllegalStateException().isThrownBy(relay(new StubTransactionManager(null))::poll).withMessage("bug");
		assertThat(output).doesNotContain(SKIPPED);
		verify(this.publisher, org.mockito.Mockito.never()).publish(any());
	}

	private OutboxRelay relay(PlatformTransactionManager transactionManager) {
		return new OutboxRelay(this.repository, this.publisher, PROPERTIES, transactionManager, clock());
	}

	private static Clock clock() {
		return Clock.fixed(NOW, ZoneOffset.UTC);
	}

	private static OutboxEvent event(String eventType) {
		return new OutboxEvent("test", UUID.randomUUID(), eventType, "{}");
	}

	private static void assertSingleWarn(CapturedOutput output, String exceptionName) {
		List<String> relayLines = output.getAll().lines().filter(line -> line.contains("OutboxRelay")).toList();
		assertThat(relayLines).hasSize(1);
		assertThat(relayLines.getFirst()).contains("WARN").contains(SKIPPED + exceptionName).doesNotContain("ERROR");
		assertThat(output).doesNotContain(DB_DETAIL).doesNotContain(exceptionName + ":").doesNotContain("\tat ");
	}

	/** {@code failure} verilmişse transaction açılamaz (DB kapalı); yoksa commit/rollback no-op. */
	private record StubTransactionManager(RuntimeException failure) implements PlatformTransactionManager {

		@Override
		public TransactionStatus getTransaction(TransactionDefinition definition) {
			if (this.failure != null) {
				throw this.failure;
			}
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
