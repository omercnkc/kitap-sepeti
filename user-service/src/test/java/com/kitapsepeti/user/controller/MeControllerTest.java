package com.kitapsepeti.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.kitapsepeti.user.ApiTestSupport;
import com.kitapsepeti.user.security.BearerChallenge;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class MeControllerTest extends ApiTestSupport {

	private static final String EMAIL = "profil@kitapsepeti.com";

	@Test
	void validTokenReturnsProfileWithoutPasswordData() throws Exception {
		String token = registerAndGetAccessToken(EMAIL);

		String body = mockMvc.perform(get("/api/me").with(bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userIdOf(EMAIL)))
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(jsonPath("$.firstName").value("Ali"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(body).doesNotContain("password").doesNotContain("passwordHash").doesNotContain("$2a$");
	}

	@Test
	void patchChangesOnlySentFieldsAndEmptyPhoneClearsIt() throws Exception {
		String token = registerAndGetAccessToken(EMAIL);

		patchMe(token, "{\"firstName\":\"Ayşe\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.firstName").value("Ayşe"))
			.andExpect(jsonPath("$.lastName").value("Veli"))
			.andExpect(jsonPath("$.phone").value("5551112233"));

		patchMe(token, "{\"phone\":\"\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value(nullValue()))
			.andExpect(jsonPath("$.firstName").value("Ayşe"));

		Map<String, Object> row = jdbc.queryForMap("SELECT first_name, last_name, phone FROM users WHERE email = ?", EMAIL);
		assertThat(row.get("first_name")).isEqualTo("Ayşe");
		assertThat(row.get("last_name")).isEqualTo("Veli");
		assertThat(row.get("phone")).isNull();
	}

	@Test
	void blankFirstNameIsRejected() throws Exception {
		String token = registerAndGetAccessToken(EMAIL);

		patchMe(token, "{\"firstName\":\"\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", hasItem("firstName")));
		patchMe(token, "{\"lastName\":\"   \"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("lastName")));

		assertThat(jdbc.queryForObject("SELECT first_name FROM users WHERE email = ?", String.class, EMAIL))
			.isEqualTo("Ali");
	}

	@Test
	void suspendedUserWithValidTokenGets403() throws Exception {
		String token = registerAndGetAccessToken(EMAIL);
		jdbc.update("UPDATE users SET status = 'suspended' WHERE email = ?", EMAIL);

		mockMvc.perform(get("/api/me").with(bearer(token)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
	}

	@Test
	void deletedUserWithValidTokenGets401() throws Exception {
		String token = registerAndGetAccessToken(EMAIL);
		jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);

		mockMvc.perform(get("/api/me").with(bearer(token)))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.INVALID_TOKEN))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private ResultActions patchMe(String token, String json) throws Exception {
		return mockMvc.perform(patch("/api/me").with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
	}

}
