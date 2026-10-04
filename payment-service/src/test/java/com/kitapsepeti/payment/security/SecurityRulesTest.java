package com.kitapsepeti.payment.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationEntryPoint;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.payment.ApiTestSupport;
import com.kitapsepeti.payment.support.InternalTestKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Üç zincir: /internal/** yalnızca {@code X-Internal-Api-Key} (401 {@code ApiKey realm="internal"}), /webhooks/**
 * yalnızca {@code POST /webhooks/*} (imza controller'da; kuralları {@code WebhookControllerTest}), geri kalan her
 * şey health ve /error dışında denyAll (403, challenge yok). Hiçbir yanıtta Bearer challenge yok.
 */
class SecurityRulesTest extends ApiTestSupport {

	@Autowired
	private FilterChainProxy filterChainProxy;

	private static final String PAYMENTS = "/internal/payments";

	private final String body = "{\"orderId\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID()
			+ "\",\"amount\":10.00,\"currency\":\"TRY\"}";

	@Test
	void missingWrongEmptyHashedOrBearerKeyIsUnauthorizedWithApiKeyChallenge() throws Exception {
		for (MockHttpServletRequestBuilder request : List.of(create(),
				create().header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.randomKey()),
				create().header(InternalApiKeyAuthenticationFilter.HEADER, ""),
				create().header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY_SHA256),
				create().header(HttpHeaders.AUTHORIZATION, "Bearer " + InternalTestKeys.ORDER_SERVICE_KEY))) {
			mockMvc.perform(request)
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, InternalApiKeyAuthenticationEntryPoint.CHALLENGE))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.instance").value(PAYMENTS));
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isZero();
	}

	/** Anahtar kontrolü yönlendirmeden önce: anahtarsız bilinmeyen internal yol da 401, anahtarlı 404. */
	@Test
	void unknownInternalPathNeedsKeyFirst() throws Exception {
		mockMvc.perform(get("/internal/other"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, InternalApiKeyAuthenticationEntryPoint.CHALLENGE));
		mockMvc.perform(get("/internal/other").header(InternalApiKeyAuthenticationFilter.HEADER,
				InternalTestKeys.ORDER_SERVICE_KEY))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void healthIsAnonymous() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
	}

	/** Sıra: internal (1) → webhook (2) → varsayılan (3); her istek ilk eşleşen zincire girer. */
	@Test
	void chainsAreOrderedInternalWebhookDefault() {
		List<SecurityFilterChain> chains = filterChainProxy.getFilterChains();
		assertThat(chains).hasSize(3);
		assertThat(firstMatchingChain(chains, "/internal/payments")).isZero();
		assertThat(firstMatchingChain(chains, "/webhooks/mock")).isEqualTo(1);
		assertThat(firstMatchingChain(chains, "/webhooks")).isEqualTo(1);
		assertThat(firstMatchingChain(chains, "/actuator/health")).isEqualTo(2);
		assertThat(firstMatchingChain(chains, "/webhooksx")).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/x", "/webhooksx", "/", "/v3/api-docs", "/internalx" })
	void everythingElseIsDeniedWithoutBearerChallenge(String path) throws Exception {
		for (MockHttpServletRequestBuilder request : List.of(get(path), post(path),
				get(path).header(HttpHeaders.AUTHORIZATION, "Bearer abc"),
				get(path).header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY))) {
			mockMvc.perform(request)
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.instance").value(path));
		}
	}

	private MockHttpServletRequestBuilder create() {
		return post(PAYMENTS).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static int firstMatchingChain(List<SecurityFilterChain> chains, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
		for (int i = 0; i < chains.size(); i++) {
			if (chains.get(i).matches(request)) {
				return i;
			}
		}
		return -1;
	}

}
