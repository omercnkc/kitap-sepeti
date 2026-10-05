package com.kitapsepeti.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.kitapsepeti.order.support.JwksServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Bağlam açılışında (Hikari, Flyway, Hibernate, AMQP, Feign) DB kullanıcı adı ve parolası, JDBC URL'de kullanıcı
 * bilgisi, RabbitMQ kimlik bilgisi ve internal API anahtarı loglanmaz. Kimlik bilgileri test sırasında üretilir; "test" gibi sık geçen bir değerle
 * yanlış negatif olmasın diye paylaşılan konteyner değil, bu değerlerle açılan ayrı bir MySQL kullanılır.
 * Broker bilerek kapalı bir porta yönlendirilir: açılıştaki exchange declare denemesi de kapsanır.
 */
@ExtendWith(OutputCaptureExtension.class)
class StartupLogHygieneTest {

	@Test
	void startupLogsContainNoCredentials(CapturedOutput output) {
		String dbUser = "ord" + randomHex(12);
		String dbPassword = randomHex(32);
		String rabbitUser = "rmq" + randomHex(12);
		String rabbitPassword = randomHex(32);
		String internalApiKey = "oik" + randomHex(32);

		try (MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4")).withDatabaseName("order_db")
			.withUsername(dbUser)
			.withPassword(dbPassword)) {
			mysql.start();
			int from = output.getAll().length();

			try (ConfigurableApplicationContext context = new SpringApplicationBuilder(OrderServiceApplication.class)
				.main(OrderServiceApplication.class)
				.run("--spring.datasource.url=" + mysql.getJdbcUrl(), "--spring.datasource.username=" + dbUser,
						"--spring.datasource.password=" + dbPassword, "--spring.rabbitmq.host=127.0.0.1",
						"--spring.rabbitmq.port=" + JwksServer.freePort(), "--spring.rabbitmq.username=" + rabbitUser,
						"--spring.rabbitmq.password=" + rabbitPassword, "--app.outbox.enabled=false",
						"--app.clients.internal-api-key=" + internalApiKey,
						"--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + JwksServer.jwkSetUri(1),
						"--server.port=0")) {
				assertThat(context.isRunning()).isTrue();
			}

			String startup = output.getAll().substring(from);
			assertThat(startup).contains("Successfully applied 2 migrations")
				.contains("Started OrderServiceApplication")
				.doesNotContain(dbUser)
				.doesNotContain(dbPassword)
				.doesNotContain(rabbitUser)
				.doesNotContain(rabbitPassword)
				.doesNotContain(internalApiKey)
				.doesNotContainPattern("jdbc:mysql://[^\\s/]*@")
				.doesNotContainPattern("(?i)[?&;](user|password)=");
		}
	}

	private static String randomHex(int length) {
		return UUID.randomUUID().toString().replace("-", "").substring(0, length);
	}

}
