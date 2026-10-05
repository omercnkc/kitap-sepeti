package com.kitapsepeti.order;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tam uygulama bağlamı açan testler için RabbitMQ. {@link TestcontainersConfiguration}'dan ayrı tutulur:
 * JDBC dilim testleri broker kullanmaz, her birinde ayrıca konteyner açılmasın. Reuse koşulu
 * {@link TestcontainersConfiguration} ile aynı; kuyruklar ve içlerindeki mesajlar koşular arasında kalır. Etiket reuse
 * hash'ini modüle özgü yapar: aynı imajlı Cart broker'ıyla paylaşılırsa relay'in yayınladığı {@code cart.checked-out}
 * mesajları Cart'ın kuyruğunda birikir.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestcontainersConfiguration {

	/** docker-compose ile aynı imaj; ayrıca indirme gerekmez. */
	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"))
			.withLabel("com.kitapsepeti.test-module", "order")
			.withReuse(true);
	}

}
