package com.kitapsepeti.common.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class DeadLetterQueueTopologyTest {

	@Test
	void createsDurableQueueDlxDlqAndAllBindings() {
		Declarables topology = DeadLetterQueueTopology.create(new TopicExchange("events", true, false), "svc.results",
				List.of("payment.succeeded", "payment.failed"), "events.dlx", "svc.results.dlq", "svc.results.dead");

		List<Queue> queues = topology.getDeclarablesByType(Queue.class);
		assertThat(queues).extracting(Queue::getName).containsExactly("svc.results", "svc.results.dlq");
		assertThat(queues).allSatisfy(queue -> {
			assertThat(queue.isDurable()).isTrue();
			assertThat(queue.isAutoDelete()).isFalse();
		});
		Queue work = queues.get(0);
		assertThat(work.getArguments())
			.containsEntry("x-dead-letter-exchange", "events.dlx")
			.containsEntry("x-dead-letter-routing-key", "svc.results.dead");

		assertThat(topology.getDeclarablesByType(DirectExchange.class))
			.singleElement()
			.satisfies(exchange -> {
				assertThat(exchange.getName()).isEqualTo("events.dlx");
				assertThat(exchange.isDurable()).isTrue();
				assertThat(exchange.isAutoDelete()).isFalse();
			});

		assertThat(topology.getDeclarablesByType(Binding.class))
			.extracting(Binding::getRoutingKey)
			.containsExactly("svc.results.dead", "payment.succeeded", "payment.failed");
	}

	@Test
	void rejectsMissingNamesAndRoutingKeys() {
		TopicExchange events = new TopicExchange("events", true, false);
		assertThatThrownBy(() -> DeadLetterQueueTopology.create(events, " ", List.of("x"), "dlx", "dlq", "dead"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DeadLetterQueueTopology.create(events, "q", List.of(), "dlx", "dlq", "dead"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DeadLetterQueueTopology.create(events, "q", List.of(" "), "dlx", "dlq", "dead"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
