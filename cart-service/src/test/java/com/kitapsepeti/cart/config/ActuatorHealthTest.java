package com.kitapsepeti.cart.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.support.CatalogStub.Response;
import com.kitapsepeti.cart.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.http.MediaType;
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
	@ValueSource(strings = { "/", "/api/cart", "/actuator", "/actuator/env", "/actuator/info" })
	void everythingElseNeedsToken(String path) throws Exception {
		mockMvc.perform(get(path))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", "Bearer"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/actuator", "/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/metrics",
			"/actuator/configprops", "/actuator/mappings", "/actuator/loggers" })
	void otherActuatorEndpointsAreNotReachable(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		// Token'la da yok: güvenlik değil, exposure ayarı kapatıyor.
		mockMvc.perform(get(path).with(bearer(TestJwt.admin(SUBJECT)))).andExpect(status().isNotFound());
	}

	@Test
	void healthIsReadOnly() throws Exception {
		mockMvc.perform(post("/actuator/health")).andExpect(status().isUnauthorized());
	}

	/** Catalog kesintisi sağlığı etkilemez; health isteği Catalog'a hiç gitmez. */
	@Test
	void healthStaysUpAndNeverCallsCatalogWhileCatalogIsDown() throws Exception {
		CATALOG.respond(Response.problem(503));

		for (String path : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
			mockMvc.perform(get(path))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
		}
		assertThat(CATALOG.requests()).isEmpty();
	}

	/** Yalnızca servisin kendi bileşenleri; Catalog, user-service (JWKS) ya da Spring Cloud kaynaklı gösterge yok. */
	@Test
	void healthContributorsAreLocalOnly() {
		List<String> names = registry.stream().map(HealthContributors.Entry::name).toList();

		assertThat(names).containsExactlyInAnyOrder("db", "diskSpace", "livenessState", "readinessState", "ping", "ssl")
			.doesNotContain("refreshScope", "discoveryComposite");
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
