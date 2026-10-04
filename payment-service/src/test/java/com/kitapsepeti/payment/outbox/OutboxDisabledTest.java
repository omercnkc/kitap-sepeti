package com.kitapsepeti.payment.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitapsepeti.payment.ApiTestSupport;
import com.kitapsepeti.payment.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/** Test profilinde {@code app.outbox.enabled=false}: worker ve zamanlayıcı hiç oluşmaz; yayıncı yine vardır. */
class OutboxDisabledTest extends ApiTestSupport {

	@Autowired
	private ApplicationContext context;

	@Test
	void relayAndSchedulerBeansAreAbsentWhenOutboxDisabled() {
		assertThat(context.getBeanNamesForType(OutboxRelay.class)).isEmpty();
		assertThat(context.getBeanNamesForType(SchedulingConfig.class)).isEmpty();
		assertThat(context.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)).isFalse();
		assertThat(context.getBeanNamesForType(TaskScheduler.class)).isEmpty();
		assertThat(context.getBeanNamesForType(OutboxPublisher.class)).hasSize(1);
	}

}
