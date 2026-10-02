package com.kitapsepeti.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;

import com.kitapsepeti.cart.config.ClockConfig;
import com.kitapsepeti.cart.service.CartProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class CartServiceApplicationTests extends ApiTestSupport {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoadsWithFeignInfrastructure() {
		assertThat(context.getBeanNamesForType(FeignClientFactory.class)).hasSize(1);
	}

	@Test
	void noGeneratedInMemoryUser() {
		assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
	}

	@Test
	void noTokenSigningInfrastructure() {
		assertThat(context.getBeanNamesForType(JwtEncoder.class)).isEmpty();
	}

	@Test
	void cartLimitsAreBound() {
		CartProperties properties = context.getBean(CartProperties.class);

		assertThat(properties.maxQuantityPerItem()).isEqualTo(10);
		assertThat(properties.maxLines()).isEqualTo(50);
	}

	@Test
	void applicationClockIsUtcAndTestsGetTheMutableClock() {
		assertThat(context.getBean(ClockConfig.class).clock().getZone()).isEqualTo(ZoneOffset.UTC);
		assertThat(context.getBean(Clock.class)).isSameAs(clock);
	}

}
