package com.kitapsepeti.cart.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.kitapsepeti.cart.TestcontainersConfiguration;
import com.kitapsepeti.cart.support.InternalTestKeys;
import com.kitapsepeti.cart.support.JwksServer;
import com.kitapsepeti.cart.support.TestJwt;
import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.security.BearerChallenge;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * JWKS ucu kesintisi (ayrı bağlam: kendi JWKS adresi). Adımlar sırayla aynı decoder önbelleğini kullanır:
 * (1) sunucu hiç açılmamışken 503, (2) kesinti kimliksiz isteği ve health'i etkilemez, (3) sunucu açılınca aynı
 * token 200 (hata önbelleğe alınmaz).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JwksOutageTest {

	private static final int PORT = JwksServer.freePort();

	private static final String WHOAMI = "/api/cart/_whoami";

	private static final String SUBJECT = UUID.randomUUID().toString();

	private static final String USER_TOKEN = TestJwt.user(SUBJECT);

	private static JwksServer jwks;

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void jwksProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> JwksServer.jwkSetUri(PORT));
		InternalTestKeys.register(registry);
	}

	@AfterAll
	static void stopServer() {
		if (jwks != null) {
			jwks.close();
		}
	}

	@Test
	@Order(1)
	void unreachableJwksReturns503ProblemDetailWithSingleWarnLine(CapturedOutput output) throws Exception {
		String body = mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, "Bearer " + USER_TOKEN))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(503))
			.andExpect(jsonPath("$.code").value("AUTHENTICATION_UNAVAILABLE"))
			.andExpect(jsonPath("$.detail").value(CommonErrorCode.AUTHENTICATION_UNAVAILABLE.defaultDetail()))
			.andExpect(jsonPath("$.instance").value(WHOAMI))
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("Exception").doesNotContain("jwks").doesNotContain("http://")
			.doesNotContain(String.valueOf(PORT)).doesNotContain(SUBJECT).doesNotContain("trace");

		assertThat(output.getOut().lines().filter(line -> line.contains("-> AUTHENTICATION_UNAVAILABLE")))
			.singleElement()
			.satisfies(line -> assertThat(line).contains(" WARN ")
				.contains("GET " + WHOAMI + " -> AUTHENTICATION_UNAVAILABLE (cause="));
		assertThat(output).doesNotContain(USER_TOKEN)
			.doesNotContain(SUBJECT)
			.doesNotContain("Bearer ")
			.doesNotContain("AuthenticationServiceException")
			.doesNotContain("Caused by")
			.doesNotContain("\tat ")
			.doesNotContain(JwksServer.jwkSetUri(PORT));
	}

	@Test
	@Order(2)
	void unreachableJwksDoesNotAffectMissingTokenOrHealth() throws Exception {
		mockMvc.perform(get(WHOAMI))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.MISSING_TOKEN))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/actuator/health/readiness"))
			.andExpect(status().isOk());
	}

	@Test
	@Order(3)
	void sameTokenSucceedsOnceJwksIsBack() throws Exception {
		jwks = JwksServer.start(TestJwt.publicJwksJson(), PORT);

		mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, "Bearer " + USER_TOKEN))
			.andExpect(status().isOk())
			.andExpect(content().string(SUBJECT));
		assertThat(jwks.requestCount()).isEqualTo(1);
	}

}
