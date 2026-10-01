package com.kitapsepeti.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tam uygulama bağlamı açan testler için RabbitMQ. {@link TestcontainersConfiguration}'dan ayrı tutulur:
 * JPA/JDBC dilim testleri broker kullanmaz, her birinde ayrıca konteyner açılmasın.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestcontainersConfiguration {

	/** docker-compose ile aynı imaj; ayrıca indirme gerekmez. */
	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));
	}

}
