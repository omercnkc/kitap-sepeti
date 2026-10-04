package com.kitapsepeti.order.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.kitapsepeti.order.ApiTestSupport;
import com.kitapsepeti.order.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

/** Compose healthcheck'inin baktığı actuator yüzeyi (payment ile aynı kapsam). Readiness yalnızca DB. */
class ActuatorHealthTest extends ApiTestSupport {

	@Autowired
	private HealthEndpointGroups groups;

	@Autowired
	private HealthContributorRegistry registry;

	@Autowired
	private PathMappedEndpoints endpoints;

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
			.andExpect(content().json("{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}",
					JsonCompareMode.STRICT));
	}

	/** denyAll: kimliksiz 401, geçerli token'la da 403; health dışında hiçbir actuator ucuna erişilmez. */
	@ParameterizedTest
	@ValueSource(strings = { "/actuator", "/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/metrics",
			"/actuator/configprops", "/actuator/mappings", "/actuator/loggers" })
	void otherActuatorEndpointsAreNotReachable(String path) throws Exception {
		mockMvc.perform(get(path))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get(path).with(bearer(TestJwt.admin(SUBJECT))))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	/** Güvenlikten bağımsız olarak da kapalı: web'e açılan tek actuator ucu health. */
	@Test
	void onlyHealthIsExposed() {
		assertThat(endpoints.getAllPaths()).containsExactly("/actuator/health");
	}

	@Test
	void healthIsReadOnly() throws Exception {
		mockMvc.perform(post("/actuator/health").with(bearer(TestJwt.user(SUBJECT)))).andExpect(status().isForbidden());
	}

	/** Yalnızca servisin kendi bileşenleri; Spring Cloud yok, başka servisi yoklayan gösterge yok. */
	@Test
	void healthContributorsAreLocalOnly() {
		List<String> names = registry.stream().map(HealthContributors.Entry::name).toList();

		assertThat(names)
			.containsExactlyInAnyOrder("db", "rabbit", "diskSpace", "livenessState", "readinessState", "ping", "ssl")
			.doesNotContain("refreshScope", "discoveryComposite");
	}

	/** RabbitMQ readiness'ta yok: broker kapalıyken olaylar outbox'ta bekler. */
	@Test
	void readinessDependsOnDatabaseOnly() {
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
