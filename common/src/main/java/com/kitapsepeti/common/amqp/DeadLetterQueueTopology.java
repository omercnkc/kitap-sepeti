package com.kitapsepeti.common.amqp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * Bir consumer kuyruğu için durable ana kuyruk + direct DLX + durable DLQ topolojisini kurar.
 * <p>
 * Auto-configuration değildir; consumer servis kendi {@code @Configuration} sınıfında dönen
 * {@link Declarables} nesnesini bean olarak kaydeder. Böylece kuyruk sahipliği consumer'da kalırken
 * dead-letter argümanları servisler arasında aynı biçimde kurulur.
 */
public final class DeadLetterQueueTopology {

	private DeadLetterQueueTopology() {
	}

	public static Declarables create(TopicExchange sourceExchange, String queueName, Collection<String> routingKeys,
			String deadLetterExchangeName, String deadLetterQueueName, String deadLetterRoutingKey) {
		Objects.requireNonNull(sourceExchange, "sourceExchange");
		String queue = requireText(queueName, "queueName");
		String dlxName = requireText(deadLetterExchangeName, "deadLetterExchangeName");
		String dlqName = requireText(deadLetterQueueName, "deadLetterQueueName");
		String dlqKey = requireText(deadLetterRoutingKey, "deadLetterRoutingKey");
		List<String> keys = List.copyOf(Objects.requireNonNull(routingKeys, "routingKeys"));
		if (keys.isEmpty()) {
			throw new IllegalArgumentException("routingKeys must not be empty");
		}

		DirectExchange deadLetterExchange = new DirectExchange(dlxName, true, false);
		Queue workQueue = QueueBuilder.durable(queue)
			.deadLetterExchange(dlxName)
			.deadLetterRoutingKey(dlqKey)
			.build();
		Queue deadLetterQueue = QueueBuilder.durable(dlqName).build();

		List<Declarable> declarables = new ArrayList<>();
		declarables.add(deadLetterExchange);
		declarables.add(workQueue);
		declarables.add(deadLetterQueue);
		declarables.add(BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(dlqKey));
		for (String key : keys) {
			declarables.add(BindingBuilder.bind(workQueue)
				.to(sourceExchange)
				.with(requireText(key, "routingKey")));
		}
		return new Declarables(declarables);
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}

}
