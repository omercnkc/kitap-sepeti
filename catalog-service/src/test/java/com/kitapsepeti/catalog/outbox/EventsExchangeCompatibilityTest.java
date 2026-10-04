package com.kitapsepeti.catalog.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitapsepeti.catalog.ApiTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Exchange user-service ile ortak. Broker'da önce user-service tanımıyla declare edilmiş exchange'e Catalog'un
 * declare'i hatasız geçmeli; tanım farklı olsaydı broker PRECONDITION_FAILED ile kanalı kapatırdı.
 */
class EventsExchangeCompatibilityTest extends ApiTestSupport {

	private static final String EXCHANGE = "kitapsepeti.events";

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private TopicExchange eventsExchange;

	@Autowired
	private ConnectionFactory connectionFactory;

	/** Broker'da user-service'in declare ettiği tanımın birebir kopyası (ortak {@code OutboxConfiguration} öncesi). */
	private static TopicExchange userServiceDefinition() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	@AfterEach
	void restoreCatalogExchange() {
		amqpAdmin.initialize();
	}

	@Test
	void catalogDefinitionEqualsUserServiceDefinition() {
		TopicExchange userService = userServiceDefinition();

		assertThat(eventsExchange.getName()).isEqualTo(EXCHANGE).isEqualTo(userService.getName());
		assertThat(eventsExchange.getType()).isEqualTo(ExchangeTypes.TOPIC).isEqualTo(userService.getType());
		assertThat(eventsExchange.isDurable()).isTrue().isEqualTo(userService.isDurable());
		assertThat(eventsExchange.isAutoDelete()).isFalse().isEqualTo(userService.isAutoDelete());
		assertThat(eventsExchange.isInternal()).isFalse().isEqualTo(userService.isInternal());
		assertThat(eventsExchange.isDelayed()).isFalse().isEqualTo(userService.isDelayed());
		assertThat(eventsExchange.getArguments()).isEmpty();
		assertThat(eventsExchange.getArguments()).isEqualTo(userService.getArguments());
	}

	@Test
	void catalogDeclareSucceedsWhenUserServiceDeclaredTheExchangeFirst() {
		amqpAdmin.deleteExchange(EXCHANGE);
		new RabbitAdmin(connectionFactory).declareExchange(userServiceDefinition());

		assertThatNoException().isThrownBy(amqpAdmin::initialize);
		assertThatNoException().isThrownBy(() -> amqpAdmin.declareExchange(eventsExchange));
	}

	/** Kontrol: broker tanım farkını gerçekten reddediyor; yukarıdaki test bu yüzden anlamlı. */
	@Test
	void brokerRejectsInequivalentRedeclaration() {
		amqpAdmin.initialize();

		assertThatThrownBy(() -> new RabbitAdmin(connectionFactory)
			.declareExchange(new TopicExchange(EXCHANGE, false, false)))
			.isInstanceOf(AmqpException.class)
			.hasStackTraceContaining("PRECONDITION_FAILED");
	}

}
