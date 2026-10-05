package com.kitapsepeti.order.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.kitapsepeti.common.security.BearerChallenge;
import com.kitapsepeti.order.ApiTestSupport;
import com.kitapsepeti.order.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * SecurityConfig kuralları ve JWT doğrulaması (token'lar gerçek HTTP JWKS ucuyla doğrulanır). {@code GET /api/orders}
 * (liste) kimlik ister: kimlik doğrulamasını geçen istek 200, geçemeyen 401.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecurityRulesTest extends ApiTestSupport {

	private static final String ORDERS = "/api/orders";

	@Test
	void ordersWithoutTokenReturns401ProblemDetail() throws Exception {
		mockMvc.perform(get(ORDERS))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.MISSING_TOKEN))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.instance").value(ORDERS));
	}

	/** CSRF kapalı ama kimlik şart: token'sız POST da 403 (CSRF) değil 401. */
	@Test
	void checkoutWithoutTokenReturns401() throws Exception {
		mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.MISSING_TOKEN))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	/**
	 * cart ile aynı: kimlikli istekte olmayan /api yolu MVC'nin 404'ü (USER ve ADMIN aynı). Tek segmentli
	 * {@code /api/orders/x} sipariş ucuna düşer (geçersiz id → 400); bu yüzden iki segmentli yol.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "/api/olmayan/yol", "/api/orders/olmayan/yol", "/api/other/ping" })
	void unknownApiPathWithValidTokenReturns404(String path) throws Exception {
		for (String token : new String[] { TestJwt.user(SUBJECT), TestJwt.admin(SUBJECT) }) {
			mockMvc.perform(get(path).with(bearer(token)))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.instance").value(path));
		}
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

	/** İmzası geçerli ama sub kullanıcı UUID'si değil: decoder'da reddedilir (cart ile aynı). */
	@ParameterizedTest
	@ValueSource(strings = { "not-a-uuid", "1-1-1-1-1", "123E4567-E89B-12D3-A456-426614174000", " " })
	void nonUuidSubjectIsRejected(String subject) throws Exception {
		assertInvalidToken(TestJwt.user(subject));
	}

	/**
	 * Order internal uç sunmaz; /internal/** ve /api dışındaki her yol denyAll: kimliksiz 401, geçerli token'la 403.
	 * Internal API anahtarı başlığı sonucu değiştirmez (order'da o zincir yok).
	 */
	@ParameterizedTest
	@ValueSource(strings = { "/internal/orders", "/internal/orders/snapshot", "/", "/orders", "/rastgele/yol",
			"/actuator/env" })
	void internalAndOtherPathsAreDenied(String path) throws Exception {
		mockMvc.perform(get(path).header("X-Internal-Api-Key", "tahmin-edilen-anahtar"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(post(path).with(bearer(TestJwt.admin(SUBJECT))))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	/** BearerTokenResolver özelleştirilmedi: token gönderilirse açık uçta da doğrulanır (probe'lar token göndermez). */
	@ParameterizedTest
	@ValueSource(strings = { "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness" })
	void healthIsAnonymousButRejectsBrokenToken(String path) throws Exception {
		mockMvc.perform(get(path))
			.andExpect(status().isOk());
		mockMvc.perform(get(path).with(bearer("bu-bir-jwt-degil")))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.INVALID_TOKEN));
	}

	@Test
	void responsesCreateNoSession() throws Exception {
		mockMvc.perform(get(ORDERS).with(bearer(TestJwt.user(SUBJECT))))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
			.andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
	}

	/** Logda yalnızca method, yol ve kod; token, sub ve token'daki diğer claim'ler (ör. e-posta) yazılmaz. */
	@Test
	void logsContainNoTokenSubjectOrClaims(CapturedOutput output) throws Exception {
		String subject = UUID.randomUUID().toString();
		String email = "gizli-kisi-5512@example.test";
		String validToken = TestJwt.userWithClaims(subject, Map.of("email", email));
		String expiredToken = TestJwt.expiredUser(subject);

		mockMvc.perform(get(ORDERS).with(bearer(validToken))).andExpect(status().isOk());
		mockMvc.perform(get(ORDERS).with(bearer(expiredToken))).andExpect(status().isUnauthorized());
		mockMvc.perform(get(ORDERS).with(bearer(TestJwt.user("not-a-uuid")))).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/internal/orders").with(bearer(validToken))).andExpect(status().isForbidden());

		assertThat(output).contains("GET " + ORDERS + " -> UNAUTHORIZED")
			.doesNotContain(validToken)
			.doesNotContain(expiredToken)
			.doesNotContain("Bearer ")
			.doesNotContain(subject)
			.doesNotContain(email)
			.doesNotContain("not-a-uuid");
	}

	private void assertInvalidToken(String token) throws Exception {
		mockMvc.perform(get(ORDERS).with(bearer(token)))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.INVALID_TOKEN))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.detail").value("Authentication is required."));
	}

}
