package com.kitapsepeti.cart.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitapsepeti.cart.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

class ActuatorHealthTest extends ApiTestSupport {

	@Autowired
	private HealthEndpointGroups groups;

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
	@ValueSource(strings = { "/", "/api/cart", "/actuator", "/actuator/env", "/actuator/info" })
	void everythingElseNeedsToken(String path) throws Exception {
		mockMvc.perform(get(path))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", "Bearer"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void healthIsReadOnly() throws Exception {
		mockMvc.perform(post("/actuator/health")).andExpect(status().isUnauthorized());
	}

	@Test
	void readinessDependsOnDatabaseOnly() {
		HealthEndpointGroup readiness = groups.get("readiness");
		assertThat(readiness.isMember("readinessState")).isTrue();
		assertThat(readiness.isMember("db")).isTrue();

		HealthEndpointGroup liveness = groups.get("liveness");
		assertThat(liveness.isMember("livenessState")).isTrue();
		assertThat(liveness.isMember("db")).isFalse();
	}

}
