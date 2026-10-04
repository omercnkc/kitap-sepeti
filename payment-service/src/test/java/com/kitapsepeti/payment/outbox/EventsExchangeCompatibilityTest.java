package com.kitapsepeti.payment.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.kitapsepeti.payment.ApiTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Exchange user-service ve catalog-service ile ortak. Broker'da önce onların tanımıyla declare edilmiş exchange'e
 * payment'ın declare'i hatasız geçmeli; tanım farklı olsaydı broker PRECONDITION_FAILED ile kanalı kapatırdı.
 */
class EventsExchangeCompatibilityTest extends ApiTestSupport {

	private static final String EXCHANGE = "kitapsepeti.events";

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private TopicExchange eventsExchange;

	@Autowired
	private ConnectionFactory connectionFactory;

	/** user-service ve catalog-service {@code RabbitConfig.eventsExchange} tanımının birebir kopyası. */
	private static TopicExchange sharedDefinition() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	@AfterEach
	void restorePaymentExchange() {
		amqpAdmin.initialize();
	}

	@Test
	void paymentDefinitionEqualsSharedDefinition() {
		TopicExchange shared = sharedDefinition();

		assertThat(eventsExchange.getName()).isEqualTo(EXCHANGE).isEqualTo(shared.getName());
		assertThat(eventsExchange.getType()).isEqualTo(ExchangeTypes.TOPIC).isEqualTo(shared.getType());
		assertThat(eventsExchange.isDurable()).isTrue().isEqualTo(shared.isDurable());
		assertThat(eventsExchange.isAutoDelete()).isFalse().isEqualTo(shared.isAutoDelete());
		assertThat(eventsExchange.isInternal()).isFalse().isEqualTo(shared.isInternal());
		assertThat(eventsExchange.isDelayed()).isFalse().isEqualTo(shared.isDelayed());
		assertThat(eventsExchange.getArguments()).isEmpty();
	}

	@Test
	void paymentDeclareSucceedsWhenAnotherServiceDeclaredTheExchangeFirst() {
		amqpAdmin.deleteExchange(EXCHANGE);
		new RabbitAdmin(connectionFactory).declareExchange(sharedDefinition());

		assertThatNoException().isThrownBy(amqpAdmin::initialize);
		assertThatNoException().isThrownBy(() -> amqpAdmin.declareExchange(eventsExchange));
	}

}
