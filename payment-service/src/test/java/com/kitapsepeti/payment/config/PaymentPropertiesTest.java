package com.kitapsepeti.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.provider.MockOutcomeRule;
import com.kitapsepeti.payment.provider.MockPaymentProvider;
import com.kitapsepeti.payment.provider.PaymentProvider;
import com.kitapsepeti.payment.provider.mock.MockWebhookSigner;
import com.kitapsepeti.payment.provider.mock.MockWebhookVerifier;
import com.kitapsepeti.payment.support.InternalTestKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class PaymentPropertiesTest {

	private static final String SECRET = InternalTestKeys.randomKey();

	private final ApplicationContextRunner withoutSecret = new ApplicationContextRunner()
		.withUserConfiguration(PaymentConfig.class, ClockConfig.class);

	private final ApplicationContextRunner runner = withoutSecret
		.withPropertyValues("app.payment.mock.webhook-secret=" + SECRET);

	@Test
	void defaultsToMockProviderAndNinetyNineFailCents() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			PaymentProperties properties = context.getBean(PaymentProperties.class);
			assertThat(properties.provider()).isEqualTo(PaymentProviderType.MOCK);
			assertThat(properties.mock().failCents()).isEqualTo(99);
			assertThat(context).getBeans(PaymentProvider.class).hasSize(1);
			assertThat(context.getBean(PaymentProvider.class)).isInstanceOf(MockPaymentProvider.class);
			assertThat(context.getBean(MockOutcomeRule.class).outcomeFor(new BigDecimal("10.99")).isSucceeded())
				.isFalse();
		});
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 50, 99 })
	void bindsFailCentsBounds(int failCents) {
		runner.withPropertyValues("app.payment.provider=mock", "app.payment.mock.fail-cents=" + failCents)
			.run(context -> assertThat(context.getBean(PaymentProperties.class).mock().failCents())
				.isEqualTo(failCents));
	}

	@ParameterizedTest
	@ValueSource(strings = { "iyzico", "paytr", "stripe" })
	void notYetSupportedProviderFailsStartup(String provider) {
		runner.withPropertyValues("app.payment.provider=" + provider).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("'" + provider + "' is not supported yet");
		});
	}

	@Test
	void emptyProviderFallsBackToDefault() {
		runner.withPropertyValues("app.payment.provider=")
			.run(context -> assertThat(context.getBean(PaymentProperties.class).provider())
				.isEqualTo(PaymentProviderType.MOCK));
	}

	@ParameterizedTest
	@ValueSource(strings = { "app.payment.provider=paypal", "app.payment.provider=MOCK_X",
			"app.payment.mock.fail-cents=100", "app.payment.mock.fail-cents=-1", "app.payment.webhook.tolerance=0s",
			"app.payment.webhook.tolerance=-5m", "app.payment.webhook.max-body-bytes=0", "app.payment.mock.delay=-1ms",
			"app.payment.mock.webhook-url=localhost:8087/webhooks/mock", "app.payment.mock.dispatch.queue-capacity=0",
			"app.payment.mock.recovery.interval=500ms", "app.payment.mock.recovery.min-age=0s",
			"app.payment.mock.recovery.batch-size=0", "app.payment.mock.recovery.batch-size=1001" })
	void invalidValuesFailStartup(String invalid) {
		runner.withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
	}

	@Test
	void webhookDefaultsAndSignerVerifierBeans() {
		runner.run(context -> {
			PaymentProperties.Webhook webhook = context.getBean(PaymentProperties.class).webhook();
			assertThat(webhook.tolerance()).isEqualTo(Duration.ofMinutes(5));
			assertThat(webhook.maxBodyBytes()).isEqualTo(65536);
			assertThat(context).hasSingleBean(MockWebhookSigner.class).hasSingleBean(MockWebhookVerifier.class);
		});
		runner.withPropertyValues("app.payment.webhook.tolerance=30s", "app.payment.webhook.max-body-bytes=1024")
			.run(context -> {
				PaymentProperties.Webhook webhook = context.getBean(PaymentProperties.class).webhook();
				assertThat(webhook.tolerance()).isEqualTo(Duration.ofSeconds(30));
				assertThat(webhook.maxBodyBytes()).isEqualTo(1024);
			});
	}

	@Test
	void mockDispatchAndRecoveryDefaults() {
		runner.run(context -> {
			PaymentProperties.Mock mock = context.getBean(PaymentProperties.class).mock();
			assertThat(mock.delay()).isEqualTo(Duration.ofMillis(500));
			assertThat(mock.webhookUrl()).isNull();
			assertThat(mock.dispatch().enabled()).isTrue();
			assertThat(mock.dispatch().queueCapacity()).isEqualTo(100);
			assertThat(mock.recovery().enabled()).isTrue();
			assertThat(mock.recovery().interval()).isEqualTo(Duration.ofSeconds(30));
			assertThat(mock.recovery().minAge()).isEqualTo(Duration.ofSeconds(10));
			assertThat(mock.recovery().batchSize()).isEqualTo(50);
		});
		runner.withPropertyValues("app.payment.mock.delay=0s", "app.payment.mock.webhook-url=",
				"app.payment.mock.dispatch.enabled=false", "app.payment.mock.recovery.batch-size=1000")
			.run(context -> {
				PaymentProperties.Mock mock = context.getBean(PaymentProperties.class).mock();
				assertThat(mock.delay()).isZero();
				assertThat(mock.dispatch().enabled()).isFalse();
				assertThat(mock.recovery().batchSize()).isEqualTo(1000);
			});
	}

	/** Secret yok / boş / 31 karakter → bağlam açılmaz; hata zincirinde değer yok. */
	@Test
	void missingEmptyOrShortWebhookSecretFailsStartupWithoutEchoingIt() {
		String shortSecret = SECRET.substring(0, 31);
		withoutSecret.run(context -> assertSecretFailure(context.getStartupFailure(), "must be set"));
		for (String invalid : List.of("", "   ", shortSecret)) {
			withoutSecret.withPropertyValues("app.payment.mock.webhook-secret=" + invalid).run(context -> {
				String messages = assertSecretFailure(context.getStartupFailure(),
						invalid.isBlank() ? "must be set" : "at least 32 characters");
				assertThat(messages).doesNotContain(shortSecret);
			});
		}
		withoutSecret.withPropertyValues("app.payment.mock.webhook-secret=" + SECRET.substring(0, 32))
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void propertiesToStringMasksTheSecret() {
		runner.run(context -> assertThat(context.getBean(PaymentProperties.class).toString())
			.contains("webhookSecret=***")
			.doesNotContain(SECRET));
	}

	@Test
	void applicationYmlReadsSecretFromEnvAndDefinesWebhookLimits() throws Exception {
		List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application",
				new ClassPathResource("application.yml"));
		PropertySource<?> yml = sources.get(0);

		assertThat(yml.getProperty("app.payment.mock.webhook-secret")).hasToString("${PAYMENT_MOCK_WEBHOOK_SECRET:}");
		assertThat(yml.getProperty("app.payment.webhook.tolerance")).hasToString("5m");
		assertThat(yml.getProperty("app.payment.webhook.max-body-bytes")).hasToString("65536");
		assertThat(yml.getProperty("app.payment.mock.delay")).hasToString("500ms");
		assertThat(yml.getProperty("app.payment.mock.webhook-url")).isNull();
		assertThat(yml.getProperty("app.payment.mock.dispatch.enabled")).hasToString("true");
		assertThat(yml.getProperty("app.payment.mock.recovery.enabled")).hasToString("true");
		assertThat(yml.getProperty("app.payment.mock.recovery.interval")).hasToString("30s");
		assertThat(yml.getProperty("app.payment.mock.recovery.min-age")).hasToString("10s");
		assertThat(yml.getProperty("app.payment.mock.recovery.batch-size")).hasToString("50");
	}

	private static String assertSecretFailure(Throwable failure, String expected) {
		assertThat(failure).isNotNull();
		StringBuilder messages = new StringBuilder();
		for (Throwable current = failure; current != null; current = current.getCause()) {
			messages.append(current).append('\n');
		}
		assertThat(messages.toString()).contains("app.payment.mock.webhook-secret", expected).doesNotContain(SECRET);
		return messages.toString();
	}

}
