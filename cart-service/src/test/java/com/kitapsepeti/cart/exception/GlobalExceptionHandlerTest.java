package com.kitapsepeti.cart.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.support.TestJwt;
import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Cart hata kodları ve GlobalExceptionHandler. Yanıtta istemcinin gönderdiği değer (kitap id'si, adet), kullanıcı id'si,
 * iç mesaj veya stack trace olmamalı; limit yanıtında yalnızca yapılandırılmış üst sınır ({@code limit}) bulunur.
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest extends ApiTestSupport {

	private static final String BASE = "/api/cart/_errors";

	private static final String BOOK_ID = UUID.randomUUID().toString();

	private static final String SENT_QUANTITY = "7319";

	@Test
	void lineLimitReturns409WithLimitOnly(CapturedOutput output) throws Exception {
		String body = assertProblem(post(BASE + "/line-limit").param("bookId", BOOK_ID), CartErrorCode.CART_LINE_LIMIT_EXCEEDED)
			.andExpect(jsonPath("$.limit").value(50))
			.andReturn().getResponse().getContentAsString();

		assertNoLeak(body, output, BOOK_ID);
		assertThat(output).contains("POST " + BASE + "/line-limit -> CART_LINE_LIMIT_EXCEEDED");
	}

	@Test
	void quantityLimitReturns409WithLimitButNotSentQuantity(CapturedOutput output) throws Exception {
		String body = assertProblem(post(BASE + "/quantity-limit").param("bookId", BOOK_ID).param("quantity", SENT_QUANTITY),
				CartErrorCode.CART_QUANTITY_LIMIT_EXCEEDED)
			.andExpect(jsonPath("$.limit").value(10))
			.andReturn().getResponse().getContentAsString();

		assertNoLeak(body, output, BOOK_ID, SENT_QUANTITY);
	}

	@Test
	void bookNotAvailableReturns409WithoutBookId(CapturedOutput output) throws Exception {
		String body = assertProblem(post(BASE + "/book-not-available").param("bookId", BOOK_ID),
				CartErrorCode.BOOK_NOT_AVAILABLE)
			.andExpect(jsonPath("$.limit").doesNotExist())
			.andExpect(jsonPath("$.bookId").doesNotExist())
			.andExpect(jsonPath("$.bookIds").doesNotExist())
			.andReturn().getResponse().getContentAsString();

		assertNoLeak(body, output, BOOK_ID);
	}

	@Test
	void catalogUnavailableReturns503WithSingleWarnLineAndCauseClassOnly(CapturedOutput output) throws Exception {
		String body = assertProblem(post(BASE + "/catalog-unavailable").param("bookId", BOOK_ID),
				CartErrorCode.CATALOG_UNAVAILABLE)
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
			.andReturn().getResponse().getContentAsString();

		assertNoLeak(body, output, BOOK_ID, ErrorProbeController.SECRET_CAUSE, "catalog-gizli-host");
		assertThat(output.getOut().lines().filter(line -> line.contains("-> CATALOG_UNAVAILABLE")))
			.singleElement()
			.satisfies(line -> assertThat(line).contains(" WARN ")
				.contains("POST " + BASE + "/catalog-unavailable -> CATALOG_UNAVAILABLE (cause=ConnectException)"));
		assertThat(output).doesNotContain("Caused by").doesNotContain("\tat ");
	}

	@ParameterizedTest
	@ValueSource(strings = { "/illegal-state", "/illegal-argument" })
	void programmingErrorsReturnGeneric500(String path, CapturedOutput output) throws Exception {
		String body = perform(get(BASE + path))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.detail").value(CommonErrorCode.INTERNAL_ERROR.defaultDetail()))
			.andExpect(jsonPath("$.instance").value(BASE + path))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(ErrorProbeController.SECRET_MESSAGE).doesNotContain("Illegal")
			.doesNotContain("Exception").doesNotContain("at com.").doesNotContain("trace");
		// Mesaj ve stack trace yalnızca sunucu logunda, ERROR seviyesinde (common davranışı).
		assertThat(output).contains(" ERROR ").contains("GET " + BASE + path + " -> INTERNAL_ERROR")
			.contains(ErrorProbeController.SECRET_MESSAGE);
	}

	/** Servisin kilitle önlediği ihlal sızarsa: 409 CONFLICT; logda yalnızca kısıt adı ve türü. */
	@Test
	void leakedActiveCartConstraintReturnsConflict(CapturedOutput output) throws Exception {
		String body = assertProblem(post(BASE + "/second-active-cart"), CommonErrorCode.CONFLICT)
			.andReturn().getResponse().getContentAsString();

		assertNoLeak(body, output, SUBJECT);
		assertThat(output).contains("POST " + BASE + "/second-active-cart -> CONFLICT")
			.contains("constraint=uk_carts_active_user, kind=UNIQUE")
			.doesNotContain("Duplicate entry");
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.with(bearer(TestJwt.user(SUBJECT))));
	}

	private ResultActions assertProblem(MockHttpServletRequestBuilder request, ErrorCode code) throws Exception {
		return perform(request)
			.andExpect(status().is(code.status().value()))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(code.status().value()))
			.andExpect(jsonPath("$.code").value(code.name()))
			.andExpect(jsonPath("$.detail").value(code.defaultDetail()))
			.andExpect(jsonPath("$.title").isString())
			.andExpect(jsonPath("$.instance").value(startsWith(BASE + "/")));
	}

	/** Yanıtta SQL/kısıt/sınıf adı/stack trace ve verilen değerler yok; değerler logda da yok. */
	private static void assertNoLeak(String body, CapturedOutput output, String... values) {
		assertThat(body).doesNotContainIgnoringCase("sql")
			.doesNotContainIgnoringCase("constraint")
			.doesNotContain("Exception")
			.doesNotContain("at com.")
			.doesNotContain("uk_").doesNotContain("fk_").doesNotContain("ck_");
		for (String value : values) {
			assertThat(body).doesNotContain(value);
			assertThat(output).doesNotContain(value);
		}
	}

}
