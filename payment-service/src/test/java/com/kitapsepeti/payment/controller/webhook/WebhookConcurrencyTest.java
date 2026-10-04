package com.kitapsepeti.payment.controller.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import com.kitapsepeti.payment.entity.Payment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Aynı ödemeye eşzamanlı webhook'lar ödeme satırının {@code FOR UPDATE} kilidinde sıraya girer: aynı olay tek kez
 * işlenir, çelişen iki sonuçtan yalnızca biri uygulanır; 500 yok.
 */
@ExtendWith(OutputCaptureExtension.class)
class WebhookConcurrencyTest extends WebhookTestSupport {

	private static final int ROUNDS = 3;

	@Test
	void tenParallelDeliveriesOfTheSameEventAreProcessedOnce(CapturedOutput output) throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			Payment payment = newPayment();
			String body = succeeded(newEventId(), payment);

			List<Integer> statuses = runConcurrently(IntStream.range(0, 10)
				.<Callable<Integer>>mapToObj(i -> () -> send(body).andReturn().getResponse().getStatus())
				.toList());

			assertThat(statuses).containsOnly(204);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_events WHERE payment_id = UUID_TO_BIN(?)",
					Integer.class, payment.getId().toString())).isEqualTo(1);
			assertThat(outboxTypes(payment.getId())).containsExactly("PaymentSucceeded");
			assertThat(paymentRow(payment.getId())).containsEntry("status", "succeeded");
		}
		assertThat(countLines(output, "outcome=APPLIED")).isEqualTo(ROUNDS);
		assertThat(countLines(output, "outcome=DUPLICATE")).isEqualTo(ROUNDS * 9);
		assertThat(output).doesNotContain(" ERROR ");
	}

	@Test
	void concurrentSucceededAndFailedForSamePaymentApplyExactlyOne(CapturedOutput output) throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			Payment payment = newPayment();
			String succeeded = succeeded(newEventId(), payment);
			String failed = failed(newEventId(), payment, "CARD_DECLINED");

			List<Integer> statuses = runConcurrently(List.<Callable<Integer>>of(
					() -> send(succeeded).andReturn().getResponse().getStatus(),
					() -> send(failed).andReturn().getResponse().getStatus()));

			assertThat(statuses).containsOnly(204);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_events WHERE payment_id = UUID_TO_BIN(?)",
					Integer.class, payment.getId().toString())).isEqualTo(2);
			String status = (String) paymentRow(payment.getId()).get("status");
			assertThat(outboxTypes(payment.getId()))
				.containsExactly(status.equals("succeeded") ? "PaymentSucceeded" : "PaymentFailed");
		}
		assertThat(countLines(output, "outcome=APPLIED")).isEqualTo(ROUNDS);
		assertThat(countLines(output, "outcome=CONFLICTING_FINAL")).isEqualTo(ROUNDS);
		assertThat(countLines(output, "Conflicting payment result ignored")).isEqualTo(ROUNDS);
		assertThat(output).doesNotContain(" ERROR ");
	}

	/**
	 * Güvenlik ağı: tekrar kontrolü bir şekilde atlanırsa (burada spy ile) olay kaydı {@code uk_provider_events_provider_event}'te
	 * düşer; transaction geri alınır, istek tekrar sayılır (204), hiçbir şey ikinci kez yazılmaz.
	 */
	@Test
	void uniqueConstraintViolationIsTreatedAsDuplicate(CapturedOutput output) throws Exception {
		Payment payment = newPayment();
		String eventId = newEventId();
		String body = succeeded(eventId, payment);
		send(body).andExpect(status().isNoContent());
		Map<String, Object> after = paymentRow(payment.getId());
		doReturn(false).when(providerEvents).existsByProviderTypeAndProviderEventId(any(), any());

		send(body).andExpect(status().isNoContent());

		assertThat(paymentRow(payment.getId())).isEqualTo(after);
		assertThat(providerEventCount()).isEqualTo(1);
		assertThat(outboxCount()).isEqualTo(1);
		// DB'nin "Duplicate entry" mesajı olay kimliğini içerir; loga hiçbir seviyede düşmemeli.
		assertThat(output).contains("outcome=DUPLICATE").doesNotContain(eventId).doesNotContain(" ERROR ");
	}

	private static long countLines(CapturedOutput output, String text) {
		return output.getAll().lines().filter(line -> line.contains(text)).count();
	}

}
