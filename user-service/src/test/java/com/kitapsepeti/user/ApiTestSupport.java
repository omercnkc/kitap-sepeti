package com.kitapsepeti.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Gerçek token'la çalışan API testlerinin ortak altyapısı. Anotasyonlar diğer Spring testleriyle aynı
 * olduğu için context (ve MySQL container'ı) paylaşılır. Her testten önce tablolar boşaltılır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

	protected static final String PASSWORD = "Gizli-Parola-7391";

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.update("DELETE FROM refresh_tokens");
		jdbc.update("DELETE FROM outbox");
		jdbc.update("DELETE FROM addresses");
		jdbc.update("DELETE FROM users");
	}

	/** Gerçek kayıt akışıyla kullanıcı oluşturur ve access token'ı döndürür. */
	protected String registerAndGetAccessToken(String email) throws Exception {
		String json = """
				{"email":"%s","password":"%s","firstName":"Ali","lastName":"Veli","phone":"5551112233"}
				""".formatted(email, PASSWORD);
		String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return JsonPath.read(body, "$.accessToken");
	}

	protected String loginAndGetAccessToken(String email) throws Exception {
		String json = """
				{"email":"%s","password":"%s"}
				""".formatted(email, PASSWORD);
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return JsonPath.read(body, "$.accessToken");
	}

	protected String userIdOf(String email) {
		return jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM users WHERE email = ?", String.class, email);
	}

	protected static RequestPostProcessor bearer(String token) {
		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
			return request;
		};
	}

}
