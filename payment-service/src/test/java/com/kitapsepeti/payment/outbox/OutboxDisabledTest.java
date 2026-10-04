package com.kitapsepeti.payment.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitapsepeti.payment.ApiTestSupport;
import com.kitapsepeti.payment.config.SchedulingConfig;
import com.kitapsepeti.payment.provider.mock.MockRecoveryJob;
import com.kitapsepeti.payment.provider.mock.MockWebhookDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/**
 * Test profilinde outbox worker ve mock kurtarma görevi kapalı: zamanlayıcı hiç oluşmaz; yayıncı ve mock webhook
 * göndericisi yine vardır.
 */
class OutboxDisabledTest extends ApiTestSupport {

	@Autowired
	private ApplicationContext context;

	@Test
	void relayAndSchedulerBeansAreAbsentWhenOutboxDisabled() {
		assertThat(context.getBeanNamesForType(OutboxRelay.class)).isEmpty();
		assertThat(context.getBeanNamesForType(MockRecoveryJob.class)).isEmpty();
		assertThat(context.getBeanNamesForType(MockWebhookDispatcher.class)).hasSize(1);
		assertThat(context.getBeanNamesForType(SchedulingConfig.class)).isEmpty();
		assertThat(context.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)).isFalse();
		assertThat(context.getBeanNamesForType(TaskScheduler.class)).isEmpty();
		assertThat(context.getBeanNamesForType(OutboxPublisher.class)).hasSize(1);
	}

}
