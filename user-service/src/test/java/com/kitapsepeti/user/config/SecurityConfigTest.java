package com.kitapsepeti.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;

import com.kitapsepeti.user.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void protectedEndpointReturns401WithoutBasicChallenge() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andReturn();

		assertThat(Objects.toString(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE), ""))
			.isEqualTo("Bearer")
			.doesNotContain("Basic");
		assertNoSession(result);
	}

	@Test
	void registerIsPermittedWithoutToken() throws Exception {
		// 401 değil 400: istek security'den geçip controller'daki doğrulamaya ulaştı.
		MvcResult result = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andReturn();

		assertNoSession(result);
	}

	@Test
	void jwksIsPermittedWithoutToken() throws Exception {
		MvcResult result = mockMvc.perform(get("/.well-known/jwks.json"))
			.andExpect(status().isOk())
			.andReturn();

		assertNoSession(result);
	}

	private static void assertNoSession(MvcResult result) {
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
			.noneMatch(cookie -> cookie.startsWith("JSESSIONID"));
		// MockMvc session açılsa bile Set-Cookie yazmaz; bu yüzden session'ın hiç oluşmadığı da kontrol edilir.
		assertThat(result.getRequest().getSession(false)).isNull();
	}

}
