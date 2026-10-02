package com.kitapsepeti.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitapsepeti.cart.service.CartProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;

// ActuatorHealthTest ile aynı yapılandırma: tek context, tek MySQL konteyneri.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CartServiceApplicationTests {

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
	void cartLimitsAreBound() {
		CartProperties properties = context.getBean(CartProperties.class);

		assertThat(properties.maxQuantityPerItem()).isEqualTo(10);
		assertThat(properties.maxLines()).isEqualTo(50);
	}

}
