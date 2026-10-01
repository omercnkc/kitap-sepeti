package com.kitapsepeti.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

/** Süre dolumu turunda hata yönetimi: tek satır WARN, ERROR ve stack trace yok. Spring context'siz. */
@ExtendWith(OutputCaptureExtension.class)
class ReservationExpiryJobFailureTest {

	private static final String SKIPPED = "Reservation expiry skipped, database unavailable: ";

	/** Exception mesajlarında geçer; loga sızarsa stack trace/mesaj basılmış demektir. */
	private static final String DB_DETAIL = "Communications link failure to mysql:3306";

	private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

	private static final UUID FIRST = UUID.randomUUID();

	private static final UUID SECOND = UUID.randomUUID();

	private static final UUID THIRD = UUID.randomUUID();

	private final StockReservationTransactions transactions = mock(StockReservationTransactions.class);

	private final ReservationExpiryJob job = new ReservationExpiryJob(transactions,
			new StockProperties(Duration.ofMinutes(15), new StockProperties.Expiry(true, Duration.ofSeconds(30), 100)),
			Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void candidatesCannotBeReadLogsSingleWarnAndEndsRound(CapturedOutput output) {
		when(transactions.findExpiredOrderIds(NOW, 100)).thenThrow(new CannotCreateTransactionException(
				"Could not open JPA EntityManager: " + DB_DETAIL, new SQLException(DB_DETAIL)));

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(0, 0));

		assertSingleWarn(output, SKIPPED + "CannotCreateTransactionException", "CannotCreateTransactionException");
		verify(transactions, never()).releaseExpired(any(), any());
	}

	@Test
	void connectionLostMidRoundLogsSingleWarnAndSkipsRemainingOrders(CapturedOutput output) {
		when(transactions.findExpiredOrderIds(any(), anyInt())).thenReturn(List.of(FIRST, SECOND, THIRD));
		when(transactions.releaseExpired(FIRST, NOW)).thenReturn(2);
		when(transactions.releaseExpired(SECOND, NOW))
			.thenThrow(new DataAccessResourceFailureException(DB_DETAIL, new SQLException(DB_DETAIL)));

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(1, 2));

		assertSingleWarn(output, SKIPPED + "DataAccessResourceFailureException", "DataAccessResourceFailureException");
		verify(transactions, never()).releaseExpired(eq(THIRD), any());
		assertThat(jobLines(output)).anySatisfy(line -> assertThat(line).contains("INFO").contains("orders=1, rows=2"));
	}

	@Test
	void transactionCannotBeOpenedForOrderEndsRound(CapturedOutput output) {
		when(transactions.findExpiredOrderIds(any(), anyInt())).thenReturn(List.of(FIRST, SECOND));
		when(transactions.releaseExpired(FIRST, NOW)).thenThrow(new CannotCreateTransactionException(DB_DETAIL));

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(0, 0));

		assertSingleWarn(output, SKIPPED + "CannotCreateTransactionException", "CannotCreateTransactionException");
		verify(transactions, never()).releaseExpired(eq(SECOND), any());
	}

	@Test
	void orderFailuresAreIsolatedAndRoundContinues(CapturedOutput output) {
		when(transactions.findExpiredOrderIds(any(), anyInt())).thenReturn(List.of(FIRST, SECOND, THIRD));
		when(transactions.releaseExpired(FIRST, NOW)).thenThrow(new IllegalStateException(DB_DETAIL));
		when(transactions.releaseExpired(SECOND, NOW)).thenThrow(new CannotAcquireLockException(DB_DETAIL));
		when(transactions.releaseExpired(THIRD, NOW)).thenReturn(1);

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(1, 1));

		List<String> warnings = jobLines(output).stream().filter(line -> line.contains("WARN")).toList();
		assertThat(warnings).hasSize(2);
		assertThat(warnings.get(0)).contains("orderId=" + FIRST).contains("error=IllegalStateException");
		assertThat(warnings.get(1)).contains("orderId=" + SECOND).contains("error=CannotAcquireLockException");
		assertThat(output).doesNotContain(DB_DETAIL).doesNotContain("\tat ").doesNotContain("ERROR");
	}

	@Test
	void nothingReleasedLogsNothing(CapturedOutput output) {
		when(transactions.findExpiredOrderIds(any(), anyInt())).thenReturn(List.of(FIRST));
		when(transactions.releaseExpired(FIRST, NOW)).thenReturn(0);

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(0, 0));

		assertThat(jobLines(output)).isEmpty();
	}

	private static void assertSingleWarn(CapturedOutput output, String message, String exceptionName) {
		List<String> warnings = jobLines(output).stream().filter(line -> line.contains("WARN")).toList();
		assertThat(warnings).hasSize(1);
		assertThat(warnings.getFirst()).contains(message);
		assertThat(output).doesNotContain(DB_DETAIL)
			.doesNotContain(exceptionName + ":")
			.doesNotContain("\tat ")
			.doesNotContain("ERROR");
	}

	private static List<String> jobLines(CapturedOutput output) {
		return output.getAll().lines().filter(line -> line.contains(".ReservationExpiryJob ")).toList();
	}

}
