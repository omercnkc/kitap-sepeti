package com.kitapsepeti.cart.config;

import java.time.Duration;
import java.util.List;

import com.kitapsepeti.cart.messaging.CartCheckoutDeadLetterRecoverer;
import com.kitapsepeti.cart.messaging.PoisonMessageException;
import com.kitapsepeti.common.amqp.DeadLetterQueueTopology;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** CartCheckedOut consumer topolojisi ve stateless retry politikası (Order'ın Payment sonucu consumer'ıyla aynı). */
@EnableRabbit
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.cart-checkouts", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CartCheckoutsConsumerConfig {

	public static final String LISTENER_ID = "cartCheckoutsListener";

	public static final String QUEUE = "cart.checkouts";

	public static final String ROUTING_KEY = "cart.checked-out";

	public static final String DEAD_LETTER_EXCHANGE = "kitapsepeti.dlx";

	public static final String DEAD_LETTER_QUEUE = "cart.checkouts.dlq";

	public static final String DEAD_LETTER_ROUTING_KEY = "cart.checkouts.dead";

	@Bean
	Declarables cartCheckoutsTopology(TopicExchange eventsExchange) {
		return DeadLetterQueueTopology.create(eventsExchange, QUEUE, List.of(ROUTING_KEY), DEAD_LETTER_EXCHANGE,
				DEAD_LETTER_QUEUE, DEAD_LETTER_ROUTING_KEY);
	}

	@Bean
	SimpleRabbitListenerContainerFactory cartCheckoutsListenerContainerFactory(
			SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		configurer.configure(factory, connectionFactory);
		factory.setPrefetchCount(10);
		factory.setConcurrentConsumers(1);
		factory.setMaxConcurrentConsumers(1);
		factory.setDefaultRequeueRejected(false);
		factory.setAdviceChain(cartCheckoutRetryInterceptor());
		// Error handler fırlatırsa container ERROR + stack trace yazar. Container istisnayı zaten yeniden fırlatır;
		// recoverer'ın AmqpRejectAndDontRequeueException'ı ile mesaj requeue edilmeden DLX'e gider.
		factory.setErrorHandler(CartCheckoutsConsumerConfig::ignoreAlreadyLogged);
		return factory;
	}

	private static MethodInterceptor cartCheckoutRetryInterceptor() {
		return RetryInterceptorBuilder.stateless()
			.configureRetryPolicy(policy -> policy.maxRetries(2)
				.delay(Duration.ofSeconds(1))
				.multiplier(2)
				.maxDelay(Duration.ofSeconds(4))
				.predicate(CartCheckoutsConsumerConfig::isRetryable))
			.recoverer(new CartCheckoutDeadLetterRecoverer())
			.build();
	}

	private static boolean isRetryable(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof PoisonMessageException) {
				return false;
			}
			current = current.getCause();
		}
		return true;
	}

	private static void ignoreAlreadyLogged(Throwable throwable) {
		// Consumer/recoverer tek satırlık güvenli özeti zaten yazdı.
	}

}
