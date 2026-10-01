package com.kitapsepeti.common.security.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/** Filtrenin tek başına davranışı; istemci yapılandırılmamışsa (özet boş) hiçbir anahtar kabul edilmez. */
class InternalApiKeyAuthenticationFilterTest {

	private static final InternalAuthProperties.Client ENABLED = new InternalAuthProperties.Client("order-service",
			TestKeys.ORDER_SERVICE_KEY_SHA256);

	private static final InternalAuthProperties.Client DISABLED = new InternalAuthProperties.Client("order-service", "");

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void disabledClientRejectsEvenTheRightKey() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter(DISABLED).doFilter(requestWithKey(TestKeys.ORDER_SERVICE_KEY), response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void matchingKeyAuthenticatesClientWithInternalRole() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		Authentication[] seen = new Authentication[1];
		MockFilterChain chain = new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest req, HttpServletResponse res) {
				seen[0] = SecurityContextHolder.getContext().getAuthentication();
			}
		});

		filter(ENABLED).doFilter(requestWithKey(TestKeys.ORDER_SERVICE_KEY), response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(seen[0].getName()).isEqualTo("order-service");
		assertThat(seen[0].isAuthenticated()).isTrue();
		assertThat(seen[0].getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactly("ROLE_INTERNAL_SERVICE");
		assertThat(seen[0].getCredentials()).isNull();
	}

	@Test
	void wrongKeyIsRejectedWithApiKeyChallenge() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter(ENABLED).doFilter(requestWithKey(TestKeys.ORDER_SERVICE_KEY + "x"), response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
			.isEqualTo(InternalApiKeyAuthenticationEntryPoint.CHALLENGE);
		assertThat(response.getContentAsString()).doesNotContain(TestKeys.ORDER_SERVICE_KEY);
		assertThat(chain.getRequest()).isNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void missingHeaderIsRejected() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter(ENABLED).doFilter(new MockHttpServletRequest("GET", "/internal/ping"), response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
			.isEqualTo(InternalApiKeyAuthenticationEntryPoint.CHALLENGE);
		assertThat(chain.getRequest()).isNull();
	}

	/** Eşleşme ilk istemcide bulunsa da her istemci {@link MessageDigest#isEqual} ile karşılaştırılır (erken çıkış yok). */
	@Test
	void comparisonIsConstantTimeAcrossAllClients() {
		InternalApiKeys keys = new InternalApiKeys(new InternalAuthProperties(List.of(ENABLED,
				new InternalAuthProperties.Client("cart-service", TestKeys.sha256Hex("another-test-only-key")))));

		try (MockedStatic<MessageDigest> digest = mockStatic(MessageDigest.class, CALLS_REAL_METHODS)) {
			assertThat(keys.clientFor(TestKeys.ORDER_SERVICE_KEY)).contains("order-service");
			digest.verify(() -> MessageDigest.isEqual(any(), any()), times(2));

			digest.clearInvocations();
			assertThat(keys.clientFor("wrong")).isEmpty();
			digest.verify(() -> MessageDigest.isEqual(any(), any()), times(2));
		}
	}

	private static InternalApiKeyAuthenticationFilter filter(InternalAuthProperties.Client client) {
		return new InternalApiKeyAuthenticationFilter(new InternalApiKeys(new InternalAuthProperties(List.of(client))),
				new InternalApiKeyAuthenticationEntryPoint(JsonMapper.builder().build()));
	}

	private static MockHttpServletRequest requestWithKey(String key) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/ping");
		request.addHeader(InternalApiKeyAuthenticationFilter.HEADER, key);
		return request;
	}

}
