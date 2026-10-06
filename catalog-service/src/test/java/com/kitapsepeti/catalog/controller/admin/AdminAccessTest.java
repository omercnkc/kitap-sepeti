package com.kitapsepeti.catalog.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Her admin controller'ı: token yok → 401, USER → 403, ADMIN → 2xx. */
class AdminAccessTest extends ApiTestSupport {

	@ParameterizedTest
	@ValueSource(strings = { "/api/admin/authors", "/api/admin/categories" })
	void onlyAdminCanUseAdminEndpoints(String path) throws Exception {
		mockMvc.perform(get(path))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get(path).with(bearer(TestJwt.user(SUBJECT))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(post(path).with(bearer(TestJwt.user(SUBJECT)))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Yetkisiz\"}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(get(path).with(bearer(TestJwt.admin(SUBJECT))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray());
		mockMvc.perform(post(path).with(bearer(TestJwt.admin(SUBJECT)))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Yetkili\"}"))
			.andExpect(status().isCreated());
	}

}
