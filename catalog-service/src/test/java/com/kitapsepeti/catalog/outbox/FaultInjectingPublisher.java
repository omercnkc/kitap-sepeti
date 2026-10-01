package com.kitapsepeti.catalog.outbox;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import com.kitapsepeti.catalog.entity.OutboxEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Gerçek yayıncıyı sarar; istenen çağrılarda broker hatası gibi exception fırlatır. Worker thread'inden çağrılır. */
class FaultInjectingPublisher extends OutboxPublisher {

	private final List<UUID> attempts = new CopyOnWriteArrayList<>();

	private volatile int failuresRemaining;

	private volatile Predicate<UUID> failOn = id -> false;

	FaultInjectingPublisher(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
		super(rabbitTemplate, properties);
	}

	@Override
	public void publish(OutboxEvent event) {
		attempts.add(event.getId());
		if (failOn.test(event.getId()) || consumeFailure()) {
			throw new OutboxPublishException("Simulated broker failure");
		}
		super.publish(event);
	}

	private synchronized boolean consumeFailure() {
		if (failuresRemaining > 0) {
			failuresRemaining--;
			return true;
		}
		return false;
	}

	synchronized void failNextCalls(int count) {
		failuresRemaining = count;
	}

	void failOn(Predicate<UUID> predicate) {
		failOn = predicate;
	}

	long attemptsFor(String id) {
		UUID uuid = UUID.fromString(id);
		return attempts.stream().filter(uuid::equals).count();
	}

	synchronized void reset() {
		failuresRemaining = 0;
		failOn = id -> false;
		attempts.clear();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		@Primary
		FaultInjectingPublisher faultInjectingPublisher(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
			return new FaultInjectingPublisher(rabbitTemplate, properties);
		}

	}

}
