package com.kitapsepeti.common.security.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@code app.internal-auth} açılış doğrulaması (servislerin internal config'indeki bean ile aynı kurulum). */
class InternalApiKeysTest {

	private static final String RAW_LOOKING_SECRET = "s3cr3t-raw-key-put-into-the-hash-property";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(KeysConfig.class)
		.withPropertyValues("app.internal-auth.clients[0].name=order-service");

	@Test
	void invalidHashFailsStartupWithoutEchoingTheValue() {
		for (String invalid : new String[] { "abc", RAW_LOOKING_SECRET, TestKeys.ORDER_SERVICE_KEY_SHA256 + "0",
				"z".repeat(64) }) {
			runner.withPropertyValues("app.internal-auth.clients[0].key-sha256=" + invalid).run(context -> {
				assertThat(context).hasFailed();
				Throwable failure = context.getStartupFailure();
				String messages = messageChain(failure);
				assertThat(messages).contains("app.internal-auth.clients[0].key-sha256")
					.contains("order-service")
					.contains("64-character hex")
					.doesNotContain(invalid);
			});
		}
	}

	@Test
	void blankNameFailsStartup() {
		new ApplicationContextRunner().withUserConfiguration(KeysConfig.class)
			.withPropertyValues("app.internal-auth.clients[0].key-sha256=" + TestKeys.ORDER_SERVICE_KEY_SHA256)
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(messageChain(context.getStartupFailure()))
					.contains("app.internal-auth.clients[0].name must not be blank")
					.doesNotContain(TestKeys.ORDER_SERVICE_KEY_SHA256);
			});
	}

	@Test
	void emptyHashDisablesClientButContextStarts() {
		runner.withPropertyValues("app.internal-auth.clients[0].key-sha256=").run(context -> {
			assertThat(context).hasNotFailed();
			InternalApiKeys keys = context.getBean(InternalApiKeys.class);
			assertThat(keys.clientFor(TestKeys.ORDER_SERVICE_KEY)).isEmpty();
			assertThat(keys.clientFor("")).isEmpty();
		});
		new ApplicationContextRunner().withUserConfiguration(KeysConfig.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(InternalApiKeys.class).clientFor(TestKeys.ORDER_SERVICE_KEY)).isEmpty();
		});
	}

	@Test
	void validHashMatchesOnlyTheRightKey() {
		runner.withPropertyValues("app.internal-auth.clients[0].key-sha256="
				+ TestKeys.ORDER_SERVICE_KEY_SHA256.toUpperCase())
			.run(context -> {
				InternalApiKeys keys = context.getBean(InternalApiKeys.class);
				assertThat(keys.clientFor(TestKeys.ORDER_SERVICE_KEY)).isEqualTo(Optional.of("order-service"));
				assertThat(keys.clientFor(TestKeys.ORDER_SERVICE_KEY + "x")).isEmpty();
				assertThat(keys.clientFor(TestKeys.ORDER_SERVICE_KEY_SHA256)).isEmpty();
			});
	}

	@Test
	void clientToStringHidesHash() {
		InternalAuthProperties.Client client = new InternalAuthProperties.Client("order-service",
				TestKeys.ORDER_SERVICE_KEY_SHA256);
		assertThat(client.toString()).contains("order-service").doesNotContain(TestKeys.ORDER_SERVICE_KEY_SHA256);
	}

	private static String messageChain(Throwable failure) {
		StringBuilder messages = new StringBuilder();
		for (Throwable current = failure; current != null; current = current.getCause()) {
			messages.append(current).append('\n');
		}
		return messages.toString();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(InternalAuthProperties.class)
	static class KeysConfig {

		@Bean
		InternalApiKeys internalApiKeys(InternalAuthProperties properties) {
			return new InternalApiKeys(properties);
		}

	}

}
