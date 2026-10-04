package com.kitapsepeti.order.config;

import java.time.Duration;
import java.util.List;

import com.kitapsepeti.common.amqp.DeadLetterQueueTopology;
import com.kitapsepeti.order.messaging.PaymentResultDeadLetterRecoverer;
import com.kitapsepeti.order.messaging.PoisonMessageException;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
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

/** Payment sonucu consumer topolojisi ve stateless retry politikası. */
@EnableRabbit
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.payment-results", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentResultsConsumerConfig {

	public static final String LISTENER_ID = "paymentResultsListener";

	public static final String QUEUE = "order.payment-results";

	public static final String DEAD_LETTER_EXCHANGE = "kitapsepeti.dlx";

	public static final String DEAD_LETTER_QUEUE = "order.payment-results.dlq";

	public static final String DEAD_LETTER_ROUTING_KEY = "order.payment-results.dead";

	@Bean
	Declarables paymentResultsTopology(TopicExchange eventsExchange) {
		return DeadLetterQueueTopology.create(eventsExchange, QUEUE,
				List.of("payment.succeeded", "payment.failed"), DEAD_LETTER_EXCHANGE, DEAD_LETTER_QUEUE,
				DEAD_LETTER_ROUTING_KEY);
	}

	@Bean
	SimpleRabbitListenerContainerFactory paymentResultsListenerContainerFactory(
			SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		configurer.configure(factory, connectionFactory);
		factory.setPrefetchCount(10);
		factory.setConcurrentConsumers(1);
		factory.setMaxConcurrentConsumers(1);
		factory.setDefaultRequeueRejected(false);
		factory.setAdviceChain(paymentResultRetryInterceptor());
		// Default ConditionalRejectingErrorHandler stack trace yazar; consumer zaten tek satır güvenli özetini yazdı.
		factory.setErrorHandler(PaymentResultsConsumerConfig::rejectWithoutFrameworkLog);
		return factory;
	}

	private static MethodInterceptor paymentResultRetryInterceptor() {
		return RetryInterceptorBuilder.stateless()
			.configureRetryPolicy(policy -> policy.maxRetries(2)
				.delay(Duration.ofSeconds(1))
				.multiplier(2)
				.maxDelay(Duration.ofSeconds(4))
				.predicate(PaymentResultsConsumerConfig::isRetryable))
			.recoverer(new PaymentResultDeadLetterRecoverer())
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

	private static void rejectWithoutFrameworkLog(Throwable throwable) {
		throw new AmqpRejectAndDontRequeueException("Payment result rejected");
	}

}
