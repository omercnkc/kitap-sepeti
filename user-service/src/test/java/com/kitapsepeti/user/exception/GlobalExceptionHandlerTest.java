package com.kitapsepeti.user.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.user.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

	private static final String BASE = "/test/exceptions";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser
	void apiExceptionBecomesProblemDetailWithCode(CapturedOutput output) throws Exception {
		mockMvc.perform(get(BASE + "/email-exists"))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
			.andExpect(jsonPath("$.detail").value(UserErrorCode.EMAIL_ALREADY_EXISTS.defaultDetail()))
			.andExpect(jsonPath("$.instance").value(BASE + "/email-exists"));

		assertThat(output).contains("GET " + BASE + "/email-exists -> EMAIL_ALREADY_EXISTS");
		// 4xx'te stack trace yok.
		assertThat(output).doesNotContain("EmailAlreadyExistsException");
	}

	@Test
	@WithMockUser
	void validationErrorsListFieldsWithoutRejectedValues(CapturedOutput output) throws Exception {
		String body = mockMvc.perform(post(BASE + "/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"gecersiz-eposta\",\"password\":\"kisa\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.instance").value(BASE + "/validate"))
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "password")))
			.andExpect(jsonPath("$.errors[0].message").isNotEmpty())
			.andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist())
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("kisa").doesNotContain("gecersiz-eposta");
		assertThat(output).contains("-> VALIDATION_FAILED");
		assertThat(output).doesNotContain("kisa").doesNotContain("gecersiz-eposta");
	}

	@Test
	@WithMockUser
	void constraintViolationBecomes400WithoutInvalidValues(CapturedOutput output) throws Exception {
		String body = mockMvc.perform(get(BASE + "/constraint-violation")
				.param("name", "cok-uzun-isim")
				.param("email", "gecersiz-posta"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.detail").value(CommonErrorCode.VALIDATION_FAILED.defaultDetail()))
			.andExpect(jsonPath("$.instance").value(BASE + "/constraint-violation"))
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[0].field").value("email"))
			.andExpect(jsonPath("$.errors[1].field").value("name"))
			.andExpect(jsonPath("$.errors[0].message").isNotEmpty())
			.andExpect(jsonPath("$.errors[0].invalidValue").doesNotExist())
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("cok-uzun-isim").doesNotContain("gecersiz-posta").doesNotContain("check.");
		assertThat(output).contains("GET " + BASE + "/constraint-violation -> VALIDATION_FAILED");
		assertThat(output).doesNotContain("cok-uzun-isim").doesNotContain("gecersiz-posta");
	}

	@Test
	@WithMockUser
	void malformedJsonDoesNotLeakParserDetails() throws Exception {
		String body = mockMvc.perform(post(BASE + "/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"a@b.com\", \"password\": "))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
			.andExpect(jsonPath("$.detail").value(CommonErrorCode.MALFORMED_REQUEST.defaultDetail()))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("com.").doesNotContain("tools.jackson").doesNotContain("Exception");
	}

	@Test
	@WithMockUser
	void dataIntegrityViolationHidesDatabaseMessage(CapturedOutput output) throws Exception {
		String body = mockMvc.perform(get(BASE + "/data-integrity"))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("uk_users_email").doesNotContain("x@y.com").doesNotContain("Duplicate");
		assertThat(output).contains("-> CONFLICT");
		assertThat(output).doesNotContain("x@y.com");
	}

	@Test
	@WithMockUser
	void unexpectedExceptionReturnsGeneric500AndLogsStackTrace(CapturedOutput output) throws Exception {
		String body = mockMvc.perform(get(BASE + "/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.detail").value(CommonErrorCode.INTERNAL_ERROR.defaultDetail()))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("gizli detay").doesNotContain("RuntimeException");
		assertThat(output).contains("ERROR").contains("-> INTERNAL_ERROR")
			.contains("java.lang.RuntimeException: gizli detay");
	}

	@Test
	@WithMockUser(roles = "USER")
	void methodSecurityDenialReturns403ProblemDetail() throws Exception {
		mockMvc.perform(get(BASE + "/admin"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.code").value("FORBIDDEN"))
			.andExpect(jsonPath("$.instance").value(BASE + "/admin"));
	}

	@Test
	void missingTokenReturns401ProblemDetailWithoutBasicChallenge() throws Exception {
		mockMvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.instance").value("/api/me"));
	}

}
