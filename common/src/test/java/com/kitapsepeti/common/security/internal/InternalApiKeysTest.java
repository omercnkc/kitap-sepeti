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
		String sha = TestKeys.ORDER_SERVICE_KEY_SHA256;
		for (String invalid : new String[] { "abc", RAW_LOOKING_SECRET, sha + "0", sha.substring(1), "z".repeat(64),
				sha.substring(0, 63) + "g", sha.substring(0, 32) + " " + sha.substring(32) }) {
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

	/** "Özet boş = istemci kapalı" yok: yok, boş ya da yalnızca boşluk olan özet de açılışı durdurur. */
	@Test
	void missingEmptyOrBlankHashFailsStartup() {
		runner.run(context -> assertMissingHashFailure(context.getStartupFailure()));
		for (String blank : new String[] { "", "   ", "\t" }) {
			runner.withPropertyValues("app.internal-auth.clients[0].key-sha256=" + blank)
				.run(context -> assertMissingHashFailure(context.getStartupFailure()));
		}
	}

	@Test
	void noConfiguredClientFailsStartup() {
		new ApplicationContextRunner().withUserConfiguration(KeysConfig.class).run(context -> {
			assertThat(context).hasFailed();
			assertThat(messageChain(context.getStartupFailure())).contains("at least one internal client");
		});
	}

	@Test
	void validHashInEitherCaseMatchesOnlyTheRightKey() {
		for (String hash : new String[] { TestKeys.ORDER_SERVICE_KEY_SHA256.toLowerCase(),
				TestKeys.ORDER_SERVICE_KEY_SHA256.toUpperCase(), " " + TestKeys.ORDER_SERVICE_KEY_SHA256 + " " }) {
			runner.withPropertyValues("app.internal-auth.clients[0].key-sha256=" + hash).run(context -> {
				assertThat(context).hasNotFailed();
				InternalApiKeys keys = context.getBean(InternalApiKeys.class);
				assertThat(keys.clientFor(TestKeys.ORDER_SERVICE_KEY)).isEqualTo(Optional.of("order-service"));
				assertThat(keys.clientFor(TestKeys.ORDER_SERVICE_KEY + "x")).isEmpty();
				assertThat(keys.clientFor(TestKeys.ORDER_SERVICE_KEY_SHA256)).isEmpty();
				assertThat(keys.clientFor("")).isEmpty();
			});
		}
	}

	@Test
	void everyConfiguredClientIsValidated() {
		runner.withPropertyValues("app.internal-auth.clients[0].key-sha256=" + TestKeys.ORDER_SERVICE_KEY_SHA256,
				"app.internal-auth.clients[1].name=cart-service", "app.internal-auth.clients[1].key-sha256=")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(messageChain(context.getStartupFailure()))
					.contains("app.internal-auth.clients[1].key-sha256")
					.contains("cart-service")
					.contains("must be set")
					.doesNotContain(TestKeys.ORDER_SERVICE_KEY_SHA256);
			});
	}

	private static void assertMissingHashFailure(Throwable failure) {
		assertThat(failure).isNotNull();
		assertThat(messageChain(failure)).contains("app.internal-auth.clients[0].key-sha256")
			.contains("order-service")
			.contains("must be set")
			.contains("64-character hex");
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
