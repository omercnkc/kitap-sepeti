package com.kitapsepeti.catalog.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.kitapsepeti.catalog.RabbitTestcontainersConfiguration;
import com.kitapsepeti.catalog.TestcontainersConfiguration;
import com.kitapsepeti.catalog.support.JwksServer;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Gerçek JWKS akışı: kendi sayaçlı sunucusu olduğu için ApiTestSupport'tan ayrı bir bağlamda koşar.
 * Bağlam açılırken JWKS'e gidilmediğini, ilk doğrulamada bir kez çekilip sonra önbellekten kullanıldığını doğrular.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, RabbitTestcontainersConfiguration.class })
class JwksFlowTest {

	private static final JwksServer JWKS = JwksServer.start(TestJwt.publicJwksJson());

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void jwksProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWKS::jwkSetUri);
	}

	@AfterAll
	static void stopServer() {
		JWKS.close();
	}

	@Test
	void keyIsFetchedLazilyOnFirstValidationAndCached() throws Exception {
		assertThat(JWKS.requestCount()).as("context started without contacting JWKS").isZero();

		String subject = UUID.randomUUID().toString();
		mockMvc.perform(get("/api/admin/ping")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.admin(subject)))
			.andExpect(status().isOk());
		assertThat(JWKS.requestCount()).isEqualTo(1);

		mockMvc.perform(get("/api/other/ping")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.user(subject)))
			.andExpect(status().isOk());
		assertThat(JWKS.requestCount()).as("second validation uses cached key").isEqualTo(1);
	}

}
