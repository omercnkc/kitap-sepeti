package com.kitapsepeti.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitapsepeti.cart.config.CartConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CartPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(CartConfig.class);

	@Test
	void bindsValidLimits() {
		runner.withPropertyValues("app.cart.max-quantity-per-item=99", "app.cart.max-lines=1").run(context -> {
			assertThat(context).hasNotFailed();
			CartProperties properties = context.getBean(CartProperties.class);
			assertThat(properties.maxQuantityPerItem()).isEqualTo(99);
			assertThat(properties.maxLines()).isEqualTo(1);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "app.cart.max-quantity-per-item=0", "app.cart.max-quantity-per-item=100",
			"app.cart.max-lines=0" })
	void rejectsOutOfRangeLimits(String invalid) {
		runner.withPropertyValues("app.cart.max-quantity-per-item=10", "app.cart.max-lines=50", invalid)
			.run(context -> assertThat(context).hasFailed());
	}

}
