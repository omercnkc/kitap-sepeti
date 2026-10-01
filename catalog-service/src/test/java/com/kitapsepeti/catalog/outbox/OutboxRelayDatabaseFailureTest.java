package com.kitapsepeti.catalog.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import com.kitapsepeti.catalog.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** DB kesintisinde OutboxRelay turu: tek satır WARN, ERROR ve stack trace yok. Spring context'siz. */
@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayDatabaseFailureTest {

	private static final String SKIPPED = "Outbox poll skipped, database unavailable: ";

	/** Exception mesajlarında geçer; loga sızarsa stack trace/mesaj basılmış demektir. */
	private static final String DB_DETAIL = "Communications link failure to mysql:3306";

	private static final OutboxProperties PROPERTIES = new OutboxProperties(true, "kitapsepeti.events",
			Duration.ofSeconds(2), 50, Duration.ofSeconds(5));

	private final OutboxRepository repository = mock(OutboxRepository.class);

	private final OutboxPublisher publisher = mock(OutboxPublisher.class);

	@Test
	void transactionCannotBeOpenedLogsSingleWarnWithoutStackTrace(CapturedOutput output) {
		RuntimeException failure = new CannotCreateTransactionException("Could not open JPA EntityManager: " + DB_DETAIL,
				new SQLException(DB_DETAIL));
		OutboxRelay relay = relay(new StubTransactionManager(failure));

		relay.poll();

		assertSingleWarn(output, "CannotCreateTransactionException");
		verifyNoInteractions(repository, publisher);
	}

	@Test
	void repositoryDataAccessFailureLogsSingleWarnWithoutStackTrace(CapturedOutput output) {
		when(repository.lockUnpublishedBatch(anyInt()))
			.thenThrow(new DataAccessResourceFailureException(DB_DETAIL, new SQLException(DB_DETAIL)));
		OutboxRelay relay = relay(new StubTransactionManager(null));

		relay.poll();

		assertSingleWarn(output, "DataAccessResourceFailureException");
		verifyNoInteractions(publisher);
	}

	@Test
	void unexpectedExceptionIsLeftToSchedulerErrorHandler(CapturedOutput output) {
		when(repository.lockUnpublishedBatch(anyInt())).thenThrow(new IllegalStateException("bug"));
		OutboxRelay relay = relay(new StubTransactionManager(null));

		assertThatIllegalStateException().isThrownBy(relay::poll).withMessage("bug");
		assertThat(output).doesNotContain(SKIPPED);
	}

	private OutboxRelay relay(PlatformTransactionManager transactionManager) {
		return new OutboxRelay(repository, publisher, PROPERTIES, transactionManager, Clock.systemUTC());
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
			if (failure != null) {
				throw failure;
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
