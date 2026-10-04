package com.kitapsepeti.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;

/** Adım 1 iskeleti: bağlam açılır, Flyway V1 uygulanır, ddl validate geçer; güvenlik/amqp/feign henüz yok. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PaymentServiceApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void contextLoadsAndFlywayAppliedV1() {
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class))
			.isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "org.springframework.security.web.SecurityFilterChain",
			"org.springframework.amqp.rabbit.core.RabbitTemplate",
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

	@ParameterizedTest
	@ValueSource(strings = { "/actuator", "/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/metrics" })
	void otherActuatorEndpointsAreNotExposed(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isNotFound());
	}

}
