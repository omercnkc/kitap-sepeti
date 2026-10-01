package com.kitapsepeti.catalog.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.kitapsepeti.catalog.RabbitTestcontainersConfiguration;
import com.kitapsepeti.catalog.TestcontainersConfiguration;
import com.kitapsepeti.catalog.exception.ErrorCode;
import com.kitapsepeti.catalog.support.JwksServer;
import com.kitapsepeti.catalog.support.TestJwt;
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
 * JWKS ucu kesintisi. Adımlar sırayla aynı bağlamı ve aynı decoder önbelleğini kullanır:
 * (1) sunucu hiç açılmamışken 503, (2) açılınca 200 (hata önbelleğe alınmaz), (3) anahtar alındıktan sonra
 * sunucu kapanınca önbellekten 200 (mevcut davranış; süre ölçülmez).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, RabbitTestcontainersConfiguration.class })
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JwksOutageTest {

	private static final int PORT = JwksServer.freePort();

	private static final String ADMIN_TOKEN = TestJwt.admin(UUID.randomUUID().toString());

	private static JwksServer jwks;

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void jwksProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> JwksServer.jwkSetUri(PORT));
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
		String body = mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(503))
			.andExpect(jsonPath("$.code").value("AUTHENTICATION_UNAVAILABLE"))
			.andExpect(jsonPath("$.detail").value(ErrorCode.AUTHENTICATION_UNAVAILABLE.defaultDetail()))
			.andExpect(jsonPath("$.instance").value("/api/admin/ping"))
			.andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("Exception").doesNotContain("jwks").doesNotContain("http://")
			.doesNotContain(String.valueOf(PORT)).doesNotContain("at org.").doesNotContain("trace");

		assertThat(output.getOut().lines().filter(line -> line.contains("-> AUTHENTICATION_UNAVAILABLE")))
			.singleElement()
			.satisfies(line -> assertThat(line).contains(" WARN ")
				.contains("GET /api/admin/ping -> AUTHENTICATION_UNAVAILABLE (cause="));
		assertThat(output).doesNotContain(ADMIN_TOKEN)
			.doesNotContain("Bearer ")
			.doesNotContain("AuthenticationServiceException")
			.doesNotContain("Caused by")
			.doesNotContain("\tat ")
			.doesNotContain(JwksServer.jwkSetUri(PORT));
	}

	@Test
	@Order(2)
	void unreachableJwksDoesNotAffectMissingTokenOrPublicReads() throws Exception {
		mockMvc.perform(get("/api/admin/ping"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.MISSING_TOKEN))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/api/books/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
			.andExpect(status().isOk());
	}

	@Test
	@Order(3)
	void sameTokenSucceedsOnceJwksIsBack() throws Exception {
		jwks = JwksServer.start(TestJwt.publicJwksJson(), PORT);

		mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
			.andExpect(status().isOk());
		assertThat(jwks.requestCount()).isEqualTo(1);
	}

	@Test
	@Order(4)
	void cachedKeyKeepsValidatingRightAfterJwksGoesDown() throws Exception {
		jwks.close();

		mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
			.andExpect(status().isOk());
	}

}
