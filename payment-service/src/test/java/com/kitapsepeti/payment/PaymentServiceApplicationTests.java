package com.kitapsepeti.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;

import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.provider.PaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Bağlam açılır, Flyway V1 + V2 uygulanır, ddl validate geçer; feign yok (springdoc Adım 7'de eklendi). Actuator
 * yüzeyi {@code config.ActuatorHealthTest}'te.
 */
class PaymentServiceApplicationTests extends ApiTestSupport {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoadsAndFlywayAppliedV1AndV2() {
		assertThat(jdbc.queryForList(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class))
			.containsExactly("1", "2");
	}

	@Test
	void singleMockProviderAndUtcClock() {
		assertThat(context.getBeansOfType(PaymentProvider.class)).hasSize(1);
		assertThat(provider.type()).isEqualTo(PaymentProviderType.MOCK);
		assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
	}

	@Test
	void feignIsNotOnClasspath() {
		assertThat(ClassUtils.isPresent("org.springframework.cloud.openfeign.FeignClient", getClass().getClassLoader()))
			.isFalse();
	}

}
