package com.kitapsepeti.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.UUID;

import com.kitapsepeti.order.config.ClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * {@code ORDER_INTERNAL_API_KEY} zorunlu: yok, boş ya da başlığa yazılamaz değerle bağlam açılmaz; hata mesajı ve
 * {@code toString()} değeri içermez. Anahtarlar test sırasında üretilir.
 */
class InternalApiKeyTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(ClientConfig.class)
		.withPropertyValues("app.clients.cart.base-url=http://127.0.0.1:1", "app.clients.catalog.base-url=http://127.0.0.1:1",
				"app.clients.payment.base-url=http://127.0.0.1:1");

	@Test
	void applicationYamlMapsTheEnvironmentVariablesWithSafeDefaults() throws IOException {
		PropertySource<?> yaml = new YamlPropertySourceLoader()
			.load("application", new ClassPathResource("application.yml"))
			.getFirst();

		assertThat(yaml.getProperty("app.clients.internal-api-key")).hasToString("${ORDER_INTERNAL_API_KEY:}");
		assertThat(yaml.getProperty("app.clients.cart.base-url")).hasToString("${ORDER_CART_URL:http://localhost:8083}");
		assertThat(yaml.getProperty("app.clients.catalog.base-url"))
			.hasToString("${ORDER_CATALOG_URL:http://localhost:8082}");
		assertThat(yaml.getProperty("app.clients.payment.base-url"))
			.hasToString("${ORDER_PAYMENT_URL:http://localhost:8087}");
	}

	@Test
	void missingKeyPreventsStartup() {
		this.runner.run(context -> assertThat(context).hasFailed()
			.getFailure()
			.satisfies(failure -> assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ORDER_INTERNAL_API_KEY")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void blankKeyPreventsStartup(String value) {
		this.runner.withPropertyValues("app.clients.internal-api-key=" + value)
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.satisfies(failure -> assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("must be set")));
	}

	@Test
	void unprintableKeyPreventsStartupWithoutEchoingIt() {
		String secret = "k" + UUID.randomUUID().toString().replace("-", "");
		String value = secret + " tail";

		assertThatThrownBy(() -> InternalApiKey.of(value)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("printable ASCII")
			.hasMessageNotContaining(secret);
		assertThatThrownBy(() -> InternalApiKey.of(secret + "\u00e7")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> InternalApiKey.of(secret + "\n")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void validKeyIsRedactedInToString() {
		String secret = "k" + UUID.randomUUID().toString().replace("-", "");

		InternalApiKey key = InternalApiKey.of(secret);

		assertThat(key.value()).isEqualTo(secret);
		assertThat(key.toString()).doesNotContain(secret).isEqualTo("InternalApiKey[redacted]");
	}

}
