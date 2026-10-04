package com.kitapsepeti.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.provider.MockOutcomeRule;
import com.kitapsepeti.payment.provider.MockPaymentProvider;
import com.kitapsepeti.payment.provider.PaymentProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PaymentPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(PaymentConfig.class);

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
			"app.payment.mock.fail-cents=100", "app.payment.mock.fail-cents=-1" })
	void invalidValuesFailStartup(String invalid) {
		runner.withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
	}

}
