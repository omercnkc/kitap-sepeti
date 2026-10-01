package com.kitapsepeti.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

class BearerTokenResolversTest {

	private static final String TOKEN = "header.payload.signature";

	@Test
	void publicGetPathsIgnoreTheAuthorizationHeader() {
		BearerTokenResolver resolver = BearerTokenResolvers.ignoringGet("/api/books/**", "/api/categories/**");

		assertThat(resolver.resolve(withToken("GET", "/api/books/abc"))).isNull();
		assertThat(resolver.resolve(withToken("GET", "/api/categories"))).isNull();
		assertThat(resolver.resolve(withToken("POST", "/api/books/abc"))).isEqualTo(TOKEN);
		assertThat(resolver.resolve(withToken("GET", "/api/admin/books"))).isEqualTo(TOKEN);
	}

	@Test
	void uriPrefixIgnoresTheAuthorizationHeaderForAnyMethod() {
		BearerTokenResolver resolver = BearerTokenResolvers.ignoringUriPrefix("/api/auth/");

		assertThat(resolver.resolve(withToken("POST", "/api/auth/refresh"))).isNull();
		assertThat(resolver.resolve(withToken("GET", "/api/me"))).isEqualTo(TOKEN);
	}

	@Test
	void otherRequestsBehaveLikeTheDefaultResolver() {
		BearerTokenResolver resolver = BearerTokenResolvers.ignoringGet("/api/books/**");

		assertThat(resolver.resolve(new MockHttpServletRequest("GET", "/api/me"))).isNull();
		MockHttpServletRequest basic = new MockHttpServletRequest("GET", "/api/me");
		basic.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
		assertThat(resolver.resolve(basic)).isNull();
	}

	private static MockHttpServletRequest withToken(String method, String uri) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
		return request;
	}

}
