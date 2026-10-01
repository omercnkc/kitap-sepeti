package com.kitapsepeti.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.test.json.JsonCompareMode;

class ActuatorHealthTest extends ApiTestSupport {

	@Autowired
	private HealthEndpointGroups groups;

	@Autowired
	private HealthContributorRegistry registry;

	@ParameterizedTest
	@ValueSource(strings = { "/actuator/health/liveness", "/actuator/health/readiness" })
	void probesArePublicAndShowOnlyStatus(String path) throws Exception {
		mockMvc.perform(get(path))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
	}

	@Test
	void rootHealthIsPublicWithoutComponentDetails() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}", JsonCompareMode.STRICT));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/actuator/env", "/actuator/beans", "/actuator/info", "/actuator" })
	void otherActuatorEndpointsAreNotReachable(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		// Token'la da yok: güvenlik değil, exposure ayarı kapatıyor.
		mockMvc.perform(get(path).with(bearer(TestJwt.admin(SUBJECT)))).andExpect(status().isNotFound());
	}

	@Test
	void readinessDependsOnDatabaseButNotOnRabbitMq() {
		assertThat(registry.getContributor("rabbit")).as("rabbit health contributor kayıtlı").isNotNull();
		assertThat(registry.getContributor("db")).as("db health contributor kayıtlı").isNotNull();

		HealthEndpointGroup readiness = groups.get("readiness");
		assertThat(readiness.isMember("readinessState")).isTrue();
		assertThat(readiness.isMember("db")).isTrue();
		assertThat(readiness.isMember("rabbit")).isFalse();

		HealthEndpointGroup liveness = groups.get("liveness");
		assertThat(liveness.isMember("livenessState")).isTrue();
		assertThat(liveness.isMember("db")).isFalse();
		assertThat(liveness.isMember("rabbit")).isFalse();
	}

}
