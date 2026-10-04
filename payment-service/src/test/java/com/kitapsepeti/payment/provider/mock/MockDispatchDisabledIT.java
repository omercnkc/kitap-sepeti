package com.kitapsepeti.payment.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * Otomatik gönderim kapalı ({@code app.payment.mock.dispatch.enabled=false}): ödeme initiated kalır; uygulama
 * gönderimden önce çökmüş gibi, webhook'u yalnızca kurtarma görevi gönderir.
 */
@TestPropertySource(properties = "app.payment.mock.dispatch.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class MockDispatchDisabledIT extends MockFlowTestSupport {

	@Test
	void paymentStaysInitiatedWhenDispatchIsDisabled() throws InterruptedException {
		ApiResponse created = create("10.00");

		Thread.sleep(DELAY.multipliedBy(2).toMillis());

		assertThat(created.status()).isEqualTo(201);
		assertThat(status(created.paymentId())).isEqualTo("initiated");
		assertThat(dispatcher.pendingCount()).isZero();
		verify(webhookSpy(), never()).handle(any(), any());
	}

	@Test
	void recoveryJobCompletesPaymentCreatedWhileDispatchWasDisabled(CapturedOutput output) {
		UUID paymentId = create("10.00").paymentId();
		assertThat(status(paymentId)).isEqualTo("initiated");
		clock.fixAt(clock.instant().plusSeconds(15));

		assertThat(recoveryJob.resendStale()).isEqualTo(1);

		assertThat(status(paymentId)).isEqualTo("succeeded");
		assertThat(get(paymentId).field("status")).isEqualTo("succeeded");
		assertThat(providerEventIds()).containsExactly("mock_evt_" + paymentId);
		assertThat(output).contains("Mock recovery resent 1 webhook(s)").doesNotContain(paymentId.toString());
	}

}
