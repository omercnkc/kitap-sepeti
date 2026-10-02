package com.kitapsepeti.cart.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.support.TestJwt;
import com.kitapsepeti.common.security.BearerChallenge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** SecurityConfig kuralları ve JWT doğrulaması (token'lar gerçek HTTP JWKS ucuyla doğrulanır). */
@ExtendWith(OutputCaptureExtension.class)
class SecurityRulesTest extends ApiTestSupport {

	private static final String WHOAMI = "/api/cart/_whoami";

	@Test
	void cartWithoutTokenReturns401ProblemDetail() throws Exception {
		mockMvc.perform(get(WHOAMI))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.MISSING_TOKEN))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.instance").value(WHOAMI));
	}

	@Test
	void userTokenResolvesUserIdFromSubject() throws Exception {
		mockMvc.perform(get(WHOAMI).with(bearer(TestJwt.user(SUBJECT))))
			.andExpect(status().isOk())
			.andExpect(content().string(SUBJECT));
	}

	@Test
	void adminTokenIsAlsoAllowed() throws Exception {
		mockMvc.perform(get(WHOAMI).with(bearer(TestJwt.admin(SUBJECT))))
			.andExpect(status().isOk())
			.andExpect(content().string(SUBJECT));
	}

	@Test
	void wrongIssuerIsRejected() throws Exception {
		assertInvalidToken(TestJwt.userWithIssuer(SUBJECT, "baska-servis"));
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		assertInvalidToken(TestJwt.expiredUser(SUBJECT));
	}

	@Test
	void tokenSignedWithForeignKeyIsRejectedEvenWithOurKid() throws Exception {
		assertInvalidToken(TestJwt.userSignedWithForeignKey(SUBJECT));
	}

	@Test
	void tamperedSignatureIsRejected() throws Exception {
		String token = TestJwt.user(SUBJECT);
		int signatureStart = token.lastIndexOf('.') + 1;
		char first = token.charAt(signatureStart);
		assertInvalidToken(token.substring(0, signatureStart) + (first == 'A' ? 'B' : 'A')
				+ token.substring(signatureStart + 1));
	}

	@Test
	void unsignedAlgNoneTokenIsRejected() throws Exception {
		assertInvalidToken(TestJwt.unsignedUser(SUBJECT));
	}

	@Test
	void malformedTokenIsRejected() throws Exception {
		assertInvalidToken("bu-bir-jwt-degil");
	}

	/** İmzası geçerli ama sub kullanıcı UUID'si değil: 500 değil 401 (decoder'da reddedilir, controller'a ulaşmaz). */
	@ParameterizedTest
	@ValueSource(strings = { "not-a-uuid", "1-1-1-1-1", "123E4567-E89B-12D3-A456-426614174000", " " })
	void nonUuidSubjectIsRejected(String subject) throws Exception {
		assertInvalidToken(TestJwt.user(subject));
	}

	@Test
	void nonUuidSubjectIsRejectedOnEveryAuthenticatedPath() throws Exception {
		mockMvc.perform(get("/api/other/ping").with(bearer(TestJwt.user("not-a-uuid"))))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.INVALID_TOKEN));
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

	/** catalog ile aynı: anyRequest().authenticated() → kimlikli istekte olmayan yol MVC'nin 404'ü. */
	@ParameterizedTest
	@ValueSource(strings = { "/api/other/olmayan-yol", "/api/cart/olmayan-yol", "/actuator/env", "/actuator/info", "/" })
	void unknownPathWithTokenReturns404(String path) throws Exception {
		mockMvc.perform(get(path).with(bearer(TestJwt.user(SUBJECT))))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.instance").value(path));
	}

	/** BearerTokenResolver özelleştirilmedi: token gönderilirse açık uçta da doğrulanır (probe'lar token göndermez). */
	@Test
	void healthIsAnonymousButRejectsBrokenToken() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health").with(bearer("bu-bir-jwt-degil")))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.INVALID_TOKEN));
	}

	@Test
	void responsesCreateNoSession() throws Exception {
		mockMvc.perform(get(WHOAMI).with(bearer(TestJwt.user(SUBJECT))))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
			.andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
	}

	/** Logda yalnızca method, yol ve kod; token, sub ve token'daki diğer claim'ler (ör. e-posta) yazılmaz. */
	@Test
	void logsContainNoTokenSubjectOrClaims(CapturedOutput output) throws Exception {
		String subject = UUID.randomUUID().toString();
		String email = "gizli-kisi-8841@example.test";
		String validToken = TestJwt.userWithClaims(subject, Map.of("email", email));
		String expiredToken = TestJwt.expiredUser(subject);

		mockMvc.perform(get(WHOAMI).with(bearer(validToken))).andExpect(status().isOk());
		mockMvc.perform(get(WHOAMI).with(bearer(expiredToken))).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/cart/olmayan-yol").with(bearer(validToken))).andExpect(status().isNotFound());
		mockMvc.perform(get(WHOAMI).with(bearer(TestJwt.user("not-a-uuid")))).andExpect(status().isUnauthorized());

		assertThat(output).contains("GET " + WHOAMI + " -> UNAUTHORIZED")
			.doesNotContain(validToken)
			.doesNotContain(expiredToken)
			.doesNotContain("Bearer ")
			.doesNotContain(subject)
			.doesNotContain(email)
			.doesNotContain("not-a-uuid");
	}

	private void assertInvalidToken(String token) throws Exception {
		mockMvc.perform(get(WHOAMI).with(bearer(token)))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.INVALID_TOKEN))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.detail").value("Authentication is required."));
	}

}
