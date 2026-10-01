package com.kitapsepeti.catalog.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.support.InternalTestKeys;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** /internal/** yalnızca X-Internal-Api-Key ile; kullanıcı JWT'leri (ADMIN dahil) geçersiz. */
@ExtendWith(OutputCaptureExtension.class)
class InternalAuthTest extends ApiTestSupport {

	private static final String PING = "/internal/ping";

	private static final String WRONG_KEY = "wrong-key-" + UUID.randomUUID();

	// --- 1. Kimlik doğrulama

	@Test
	void missingKeyIsRejected() throws Exception {
		assertUnauthorized(get(PING));
		assertUnauthorized(post("/internal/stock/reservations").contentType(MediaType.APPLICATION_JSON).content("{}"));
		assertUnauthorized(get(PING).with(InternalTestKeys.apiKey("")));
	}

	@Test
	void wrongKeyIsRejected() throws Exception {
		assertUnauthorized(get(PING).with(InternalTestKeys.apiKey(WRONG_KEY)));
		// Özetin kendisi anahtar yerine geçmez.
		assertUnauthorized(get(PING).with(InternalTestKeys.apiKey(InternalTestKeys.ORDER_SERVICE_KEY_SHA256)));
		assertUnauthorized(get(PING).with(InternalTestKeys.apiKey(InternalTestKeys.ORDER_SERVICE_KEY + " ")));
	}

	@Test
	void userAndAdminJwtsAreRejected() throws Exception {
		assertUnauthorized(get(PING).with(bearer(TestJwt.user(SUBJECT))));
		assertUnauthorized(get(PING).with(bearer(TestJwt.admin(SUBJECT))));
		assertUnauthorized(get("/internal/stock/reservations/" + UUID.randomUUID()).with(bearer(TestJwt.admin(SUBJECT))));
	}

	@Test
	void correctKeyIsAccepted() throws Exception {
		mockMvc.perform(get(PING).with(InternalTestKeys.orderServiceKey()))
			.andExpect(status().isOk())
			.andExpect(content().string("ok"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		// Geçerli anahtarla birlikte gelen kullanıcı token'ı yok sayılır.
		mockMvc.perform(get(PING).with(InternalTestKeys.orderServiceKey()).with(bearer("bu-bir-jwt-degil")))
			.andExpect(status().isOk());
	}

	// --- 2. Loglar

	@Test
	void logsNeverContainKeyOrHash(CapturedOutput output) throws Exception {
		int before = countOccurrences(output.getAll(), "Rejected internal request GET " + PING + " -> UNAUTHORIZED");

		assertUnauthorized(get(PING).with(InternalTestKeys.apiKey(WRONG_KEY)));
		mockMvc.perform(get(PING).with(InternalTestKeys.orderServiceKey())).andExpect(status().isOk());

		String logs = output.getAll();
		assertThat(countOccurrences(logs, "Rejected internal request GET " + PING + " -> UNAUTHORIZED"))
			.isEqualTo(before + 1);
		assertThat(logs).contains("WARN").contains("Internal request GET " + PING + " client=order-service");
		assertThat(logs).doesNotContain(WRONG_KEY)
			.doesNotContain(InternalTestKeys.ORDER_SERVICE_KEY)
			.doesNotContainIgnoringCase(InternalTestKeys.ORDER_SERVICE_KEY_SHA256);
	}

	private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
		mockMvc.perform(request)
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "ApiKey realm=\"internal\""))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.detail").value("Authentication is required."));
	}

	private static int countOccurrences(String text, String needle) {
		Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(text);
		int count = 0;
		while (matcher.find()) {
			count++;
		}
		return count;
	}

}
