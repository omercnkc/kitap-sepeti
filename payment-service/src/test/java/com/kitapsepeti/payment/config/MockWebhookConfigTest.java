package com.kitapsepeti.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kitapsepeti.payment.provider.mock.MockRecoveryJob;
import com.kitapsepeti.payment.provider.mock.MockWebhookDispatcher;
import com.kitapsepeti.payment.repository.PaymentRepository;
import com.kitapsepeti.payment.support.InternalTestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import tools.jackson.databind.json.JsonMapper;

/** Gönderici, kurtarma görevi ve zamanlayıcı hangi ayarlarla oluşur. */
class MockWebhookConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(PaymentConfig.class, ClockConfig.class, MockWebhookConfig.class, SchedulingConfig.class)
		.withBean(PaymentRepository.class, () -> mock(PaymentRepository.class))
		.withBean(JsonMapper.class, () -> JsonMapper.builder().build())
		.withPropertyValues("app.payment.mock.webhook-secret=" + InternalTestKeys.randomKey(),
				"app.payment.mock.recovery.interval=30s");

	@Test
	void byDefaultDispatcherRecoveryJobAndSchedulingAreActive() {
		runner.run(context -> {
			assertThat(context).hasNotFailed()
				.hasSingleBean(MockWebhookDispatcher.class)
				.hasSingleBean(MockRecoveryJob.class)
				.hasSingleBean(SchedulingConfig.class);
			assertThat(context.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))
				.isTrue();
		});
	}

	@Test
	void disabledRecoveryWithoutOutboxStartsNoScheduler() {
		runner.withPropertyValues("app.payment.mock.recovery.enabled=false", "app.outbox.enabled=false")
			.run(context -> {
				assertThat(context).hasNotFailed()
					.hasSingleBean(MockWebhookDispatcher.class)
					.doesNotHaveBean(MockRecoveryJob.class)
					.doesNotHaveBean(SchedulingConfig.class);
				assertThat(context.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))
					.isFalse();
			});
	}

	@Test
	void outboxAloneStillEnablesScheduling() {
		runner.withPropertyValues("app.payment.mock.recovery.enabled=false", "app.outbox.enabled=true")
			.run(context -> assertThat(context).doesNotHaveBean(MockRecoveryJob.class)
				.hasSingleBean(SchedulingConfig.class));
	}

	@Test
	void disabledDispatchKeepsTheSenderForRecovery() {
		runner.withPropertyValues("app.payment.mock.dispatch.enabled=false")
			.run(context -> assertThat(context).hasSingleBean(MockWebhookDispatcher.class)
				.hasSingleBean(MockRecoveryJob.class));
	}

}
