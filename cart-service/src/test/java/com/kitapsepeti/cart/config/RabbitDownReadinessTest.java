package com.kitapsepeti.cart.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitapsepeti.cart.TestcontainersConfiguration;
import com.kitapsepeti.cart.support.InternalTestKeys;
import com.kitapsepeti.cart.support.JwksServer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Broker adresinde dinleyen yok, consumer açık (ayrı bağlam). Diğer servislerle aynı: uygulama açılır, readiness UP
 * (RabbitMQ readiness'ta değil); yalnızca kök health broker'ı DOWN gösterir. Listener bağlantıyı arka planda yeniler.
 */
@SpringBootTest(properties = "app.cart-checkouts.enabled=true")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RabbitDownReadinessTest {

	private static final int CLOSED_PORT = JwksServer.freePort();

	@Autowired
	private HealthEndpoint healthEndpoint;

	@Autowired
	private RabbitListenerEndpointRegistry listenerRegistry;

	@DynamicPropertySource
	static void brokerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
		registry.add("spring.rabbitmq.port", () -> CLOSED_PORT);
		InternalTestKeys.register(registry);
	}

	@Test
	void startsAndStaysReadyWhileBrokerIsDown() {
		assertThat(listenerRegistry.getListenerContainer(CartCheckoutsConsumerConfig.LISTENER_ID)).isNotNull();
		assertThat(healthEndpoint.healthForPath("readiness").getStatus()).isEqualTo(Status.UP);
		assertThat(healthEndpoint.healthForPath("liveness").getStatus()).isEqualTo(Status.UP);
		assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.DOWN);
	}

}
