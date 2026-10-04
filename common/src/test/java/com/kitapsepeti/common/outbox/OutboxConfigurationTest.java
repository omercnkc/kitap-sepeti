package com.kitapsepeti.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** Exchange'in tek tanımı ve relay'in yalnızca {@code app.outbox.enabled=true} iken oluşması. */
class OutboxConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(OutboxConfiguration.class)
		.withBean(EntityManager.class, () -> mock(EntityManager.class))
		.withBean(JsonMapper.class, () -> JsonMapper.builder().build())
		.withBean(OutboxRepository.class, () -> mock(OutboxRepository.class))
		.withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
		.withBean(Clock.class, Clock::systemUTC)
		.withBean(OutboxPublisher.class, () -> new OutboxPublisher(mock(RabbitTemplate.class),
				new OutboxProperties(true, "kitapsepeti.events", java.time.Duration.ofSeconds(2), 50,
						java.time.Duration.ofSeconds(5)),
				OutboxRoutingKeys.of(Map.of())))
		.withPropertyValues("app.outbox.exchange=kitapsepeti.events", "app.outbox.poll-interval=2s",
				"app.outbox.batch-size=50", "app.outbox.confirm-timeout=5s");

	@Test
	void exchangeIsDurableNonAutoDeleteTopicWithoutArguments() {
		this.runner.withPropertyValues("app.outbox.enabled=false").run(context -> {
			TopicExchange exchange = context.getBean("eventsExchange", TopicExchange.class);
			assertThat(exchange.getName()).isEqualTo("kitapsepeti.events");
			assertThat(exchange.isDurable()).isTrue();
			assertThat(exchange.isAutoDelete()).isFalse();
			assertThat(exchange.isInternal()).isFalse();
			assertThat(exchange.getArguments()).isEmpty();
			assertThat(context).hasSingleBean(OutboxService.class).doesNotHaveBean(OutboxRelay.class);
		});
	}

	@Test
	void relayExistsOnlyWhenEnabled() {
		this.runner.withPropertyValues("app.outbox.enabled=true")
			.run(context -> assertThat(context).hasSingleBean(OutboxRelay.class));
	}

	@Test
	void invalidPropertiesFailStartup() {
		this.runner.withPropertyValues("app.outbox.batch-size=0").run(context -> assertThat(context).hasFailed());
	}

}
