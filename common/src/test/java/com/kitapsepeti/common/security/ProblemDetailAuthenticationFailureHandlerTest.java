package com.kitapsepeti.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import tools.jackson.databind.json.JsonMapper;

class ProblemDetailAuthenticationFailureHandlerTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final ProblemDetailAuthenticationFailureHandler handler = new ProblemDetailAuthenticationFailureHandler(
			new ProblemDetailAuthenticationEntryPoint(this.jsonMapper), this.jsonMapper);

	@Test
	void verificationInfrastructureFailureIs503() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.handler.onAuthenticationFailure(new MockHttpServletRequest("GET", "/api/admin/books"), response,
				new AuthenticationServiceException("JWKS http://user-service/... unreachable",
						new ConnectException("refused")));

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(response.getContentAsString()).contains("AUTHENTICATION_UNAVAILABLE").doesNotContain("user-service");
		assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
	}

	@Test
	void invalidTokenGoesToTheEntryPointAs401() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.handler.onAuthenticationFailure(new MockHttpServletRequest("GET", "/api/admin/books"), response,
				new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_TOKEN));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(BearerChallenge.INVALID_TOKEN);
		assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
	}

}
