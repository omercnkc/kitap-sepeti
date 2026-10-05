package com.kitapsepeti.common.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.TopicExchange;

class EventsExchangeTest {

	@Test
	void durableNonAutoDeleteTopicWithoutArguments() {
		TopicExchange exchange = EventsExchange.create("kitapsepeti.events");

		assertThat(exchange.getName()).isEqualTo("kitapsepeti.events");
		assertThat(exchange.getType()).isEqualTo(ExchangeTypes.TOPIC);
		assertThat(exchange.isDurable()).isTrue();
		assertThat(exchange.isAutoDelete()).isFalse();
		assertThat(exchange.isInternal()).isFalse();
		assertThat(exchange.getArguments()).isEmpty();
	}

	@Test
	void rejectsBlankName() {
		assertThatThrownBy(() -> EventsExchange.create(" ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> EventsExchange.create(null)).isInstanceOf(IllegalArgumentException.class);
	}

}
