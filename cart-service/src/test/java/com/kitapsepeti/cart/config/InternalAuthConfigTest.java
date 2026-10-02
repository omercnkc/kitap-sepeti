package com.kitapsepeti.cart.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.kitapsepeti.cart.support.InternalTestKeys;
import com.kitapsepeti.common.security.internal.InternalApiKeys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * {@code app.internal-auth} açılış doğrulaması: özet yok ya da bozuksa bağlam açılmaz, hata mesajı değeri yansıtmaz.
 * Biçim kuralı catalog'unkiyle aynı (common {@link InternalApiKeys}); boş özetin reddi cart'a özgü.
 */
class InternalAuthConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(InternalAuthConfig.class);

	private final ApplicationContextRunner orderClient = runner
		.withPropertyValues("app.internal-auth.clients[0].name=order-service");

	@Test
	void missingOrEmptyHashFailsStartup() {
		orderClient.run(context -> assertFailure(context.getStartupFailure(),
				"app.internal-auth.clients[0].key-sha256", "order-service", "must be set"));
		orderClient.withPropertyValues("app.internal-auth.clients[0].key-sha256=").run(context -> assertFailure(
				context.getStartupFailure(), "app.internal-auth.clients[0].key-sha256", "must be set"));
		orderClient.withPropertyValues("app.internal-auth.clients[0].key-sha256=   ").run(context -> assertFailure(
				context.getStartupFailure(), "app.internal-auth.clients[0].key-sha256", "must be set"));
		runner.run(context -> assertFailure(context.getStartupFailure(), "at least one internal client"));
	}

	@Test
	void malformedHashFailsStartupWithoutEchoingTheValue() {
		String rawKey = InternalTestKeys.randomKey();
		for (String invalid : List.of("abc", rawKey, InternalTestKeys.ORDER_SERVICE_KEY_SHA256 + "0",
				InternalTestKeys.ORDER_SERVICE_KEY_SHA256.substring(1), "g".repeat(64))) {
			orderClient.withPropertyValues("app.internal-auth.clients[0].key-sha256=" + invalid).run(context -> {
				String messages = assertFailure(context.getStartupFailure(), "app.internal-auth.clients[0].key-sha256",
						"order-service", "64-character hex");
				assertThat(messages).doesNotContain(invalid);
			});
		}
	}

	@Test
	void validHashStartsAndMatchesOnlyTheRightKey() {
		orderClient.withPropertyValues("app.internal-auth.clients[0].key-sha256=" + InternalTestKeys.ORDER_SERVICE_KEY_SHA256)
			.run(context -> {
				assertThat(context).hasNotFailed();
				InternalApiKeys keys = context.getBean(InternalApiKeys.class);
				assertThat(keys.clientFor(InternalTestKeys.ORDER_SERVICE_KEY)).isEqualTo(Optional.of("order-service"));
				assertThat(keys.clientFor(InternalTestKeys.ORDER_SERVICE_KEY_SHA256)).isEmpty();
			});
	}

	@Test
	void applicationYmlReadsOrderHashFromCartEnvVariable() throws Exception {
		List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application",
				new ClassPathResource("application.yml"));
		PropertySource<?> yml = sources.get(0);

		assertThat(yml.getProperty("app.internal-auth.clients[0].name")).hasToString("order-service");
		assertThat(yml.getProperty("app.internal-auth.clients[0].key-sha256"))
			.hasToString("${CART_INTERNAL_KEY_ORDER_SHA256:}");
	}

	private static String assertFailure(Throwable failure, String... expectedParts) {
		assertThat(failure).isNotNull();
		StringBuilder messages = new StringBuilder();
		for (Throwable current = failure; current != null; current = current.getCause()) {
			messages.append(current).append('\n');
		}
		assertThat(messages.toString()).contains(expectedParts).doesNotContain(InternalTestKeys.ORDER_SERVICE_KEY);
		return messages.toString();
	}

}
