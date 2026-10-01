package com.kitapsepeti.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/**
 * {@code app.stock.expiry.enabled=false} (test profili): görev ve zamanlayıcı hiç oluşmaz. Koşulların kendisi ayrıca
 * küçük bir context'te denenir: zamanlayıcı outbox VEYA süre dolumu açıkken kurulur.
 */
class ReservationExpiryDisabledTest extends ApiTestSupport {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(SchedulingConfig.class, ReservationExpiryJob.class)
		.withBean(StockReservationTransactions.class, () -> mock(StockReservationTransactions.class))
		.withBean(StockProperties.class,
				() -> new StockProperties(Duration.ofMinutes(15),
						new StockProperties.Expiry(true, Duration.ofHours(1), 100)))
		.withBean(Clock.class, Clock::systemUTC)
		.withPropertyValues("app.stock.expiry.interval=1h");

	@Autowired
	private ApplicationContext context;

	@Test
	void jobAndSchedulerBeansAreAbsentWhenExpiryDisabled() {
		assertThat(context.getBeanNamesForType(ReservationExpiryJob.class)).isEmpty();
		assertThat(context.getBeanNamesForType(SchedulingConfig.class)).isEmpty();
		assertThat(context.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)).isFalse();
		assertThat(context.getBeanNamesForType(TaskScheduler.class)).isEmpty();
		assertThat(context.getBeanNamesForType(StockReservationTransactions.class)).hasSize(1);
	}

	@Test
	void expiryAloneEnablesJobAndScheduling() {
		runner.withPropertyValues("app.stock.expiry.enabled=true", "app.outbox.enabled=false").run(context -> {
			assertThat(context).hasSingleBean(ReservationExpiryJob.class).hasSingleBean(SchedulingConfig.class);
			assertThat(context).hasBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
		});
	}

	@Test
	void outboxAloneEnablesSchedulingButNotJob() {
		runner.withPropertyValues("app.stock.expiry.enabled=false", "app.outbox.enabled=true").run(context -> {
			assertThat(context).doesNotHaveBean(ReservationExpiryJob.class).hasSingleBean(SchedulingConfig.class);
		});
	}

	@Test
	void bothDisabledCreatesNeither() {
		runner.withPropertyValues("app.stock.expiry.enabled=false", "app.outbox.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(ReservationExpiryJob.class).doesNotHaveBean(SchedulingConfig.class);
			assertThat(context).doesNotHaveBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
		});
	}

}
