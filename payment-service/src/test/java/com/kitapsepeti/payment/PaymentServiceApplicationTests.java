package com.kitapsepeti.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.ZoneOffset;

import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.provider.PaymentProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.util.ClassUtils;

/** Bağlam açılır, Flyway V1 + V2 uygulanır, ddl validate geçer; amqp/feign/springdoc henüz yok. */
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

	@ParameterizedTest
	@ValueSource(strings = { "org.springframework.amqp.rabbit.core.RabbitTemplate",
			"org.springframework.cloud.openfeign.FeignClient",
			"org.springdoc.core.configuration.SpringDocConfiguration" })
	void laterStepDependenciesAreNotOnClasspath(String className) {
		assertThat(ClassUtils.isPresent(className, getClass().getClassLoader())).isFalse();
	}

	@Test
	void healthProbesAreUpWithoutDetails() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}", true));
		mockMvc.perform(get("/actuator/health/liveness"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"status\":\"UP\"}", true));
		mockMvc.perform(get("/actuator/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"status\":\"UP\"}", true));
	}

	/** Güvenlik eklenince (Adım 3) diğer actuator yolları varsayılan zincirin denyAll'una takılır: 403, challenge yok. */
	@ParameterizedTest
	@ValueSource(strings = { "/actuator", "/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/metrics" })
	void otherActuatorEndpointsAreDenied(String path) throws Exception {
		mockMvc.perform(get(path))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"))
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
	}

}
