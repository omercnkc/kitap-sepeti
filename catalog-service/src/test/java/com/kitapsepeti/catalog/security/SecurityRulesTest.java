package com.kitapsepeti.catalog.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** SecurityConfig kuralları ve JWT doğrulaması (token'lar gerçek HTTP JWKS ucuyla doğrulanır). */
class SecurityRulesTest extends ApiTestSupport {

	@ParameterizedTest
	@ValueSource(strings = { "/api/books/ping", "/api/categories/ping" })
	void publicCatalogGetNeedsNoToken(String path) throws Exception {
		mockMvc.perform(get(path))
			.andExpect(status().isOk());
	}

	@Test
	void publicCatalogGetIgnoresMalformedOrExpiredToken() throws Exception {
		mockMvc.perform(get("/api/books/ping").with(bearer("bu-bir-jwt-degil")))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/books/ping").with(bearer(TestJwt.expiredAdmin(SUBJECT))))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/categories/ping").with(bearer(TestJwt.expiredAdmin(SUBJECT))))
			.andExpect(status().isOk());
	}

	@Test
	void writeOnPublicPathRequiresToken() throws Exception {
		mockMvc.perform(post("/api/books/ping"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void adminWithoutTokenReturns401ProblemDetail() throws Exception {
		mockMvc.perform(get("/api/admin/ping"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.MISSING_TOKEN))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.instance").value("/api/admin/ping"));
	}

	@Test
	void adminWithUserRoleReturns403ProblemDetail() throws Exception {
		mockMvc.perform(get("/api/admin/ping").with(bearer(TestJwt.user(SUBJECT))))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("FORBIDDEN"))
			.andExpect(jsonPath("$.instance").value("/api/admin/ping"));
	}

	@Test
	void adminWithAdminRoleReturns200() throws Exception {
		mockMvc.perform(get("/api/admin/ping").with(bearer(TestJwt.admin(SUBJECT))))
			.andExpect(status().isOk());
	}

	@Test
	void wrongIssuerIsRejected() throws Exception {
		assertInvalidToken(TestJwt.adminWithIssuer(SUBJECT, "baska-servis"));
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		assertInvalidToken(TestJwt.expiredAdmin(SUBJECT));
	}

	@Test
	void tokenSignedWithForeignKeyIsRejectedEvenWithOurKid() throws Exception {
		assertInvalidToken(TestJwt.adminSignedWithForeignKey(SUBJECT));
	}

	@Test
	void unsignedAlgNoneTokenIsRejected() throws Exception {
		assertInvalidToken(TestJwt.unsignedAdmin(SUBJECT));
	}

	@Test
	void internalPathIsDeniedEvenForAdmin() throws Exception {
		mockMvc.perform(get("/internal/ping").with(bearer(TestJwt.admin(SUBJECT))))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void otherPathsRequireAnyAuthenticatedUser() throws Exception {
		mockMvc.perform(get("/api/other/ping"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/api/other/ping").with(bearer(TestJwt.user(SUBJECT))))
			.andExpect(status().isOk())
			.andExpect(content().string(SUBJECT));
	}

	@Test
	void responsesCreateNoSession() throws Exception {
		mockMvc.perform(get("/api/other/ping").with(bearer(TestJwt.user(SUBJECT))))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
			.andExpect(result -> {
				if (result.getRequest().getSession(false) != null) {
					throw new AssertionError("HTTP session must not be created");
				}
			});
	}

	private void assertInvalidToken(String token) throws Exception {
		mockMvc.perform(get("/api/admin/ping").with(bearer(token)))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.INVALID_TOKEN))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.detail").value("Authentication is required."));
	}

}
