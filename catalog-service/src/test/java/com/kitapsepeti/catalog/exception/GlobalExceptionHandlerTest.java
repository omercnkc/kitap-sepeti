package com.kitapsepeti.catalog.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.Publisher;
import com.kitapsepeti.catalog.repository.BookRepository;
import com.kitapsepeti.catalog.repository.PublisherRepository;
import com.kitapsepeti.catalog.support.TestJwt;
import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * GlobalExceptionHandler: DB kısıt ihlalleri gerçek MySQL'de tetiklenir. Yanıtta ve logda SQL metni,
 * çakışan değer veya stack trace olmamalı; logda yalnızca kısıt adı ve türü bulunur.
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest extends ApiTestSupport {

	private static final String BASE = "/test/errors";

	private static final String SECRET_SLUG = "gizli-slug-degeri-7731";

	private static final String SECRET_ISBN = "9786059998877";

	@Autowired
	private PublisherRepository publisherRepository;

	@Autowired
	private BookRepository bookRepository;

	@Test
	void duplicatePublisherSlugReturnsSlugAlreadyExists(CapturedOutput output) throws Exception {
		String request = "{\"name\":\"Yayınevi\",\"slug\":\"" + SECRET_SLUG + "\"}";
		perform(post(BASE + "/publishers").contentType(MediaType.APPLICATION_JSON).content(request))
			.andExpect(status().isOk());

		String body = assertConflict(post(BASE + "/publishers").contentType(MediaType.APPLICATION_JSON).content(request),
				CatalogErrorCode.SLUG_ALREADY_EXISTS);

		assertNoLeak(body, output, SECRET_SLUG);
		assertThat(output).contains("POST " + BASE + "/publishers -> SLUG_ALREADY_EXISTS")
			.contains("constraint=uk_publishers_slug, kind=UNIQUE");
	}

	@Test
	void duplicateIsbnReturnsIsbnAlreadyExists(CapturedOutput output) throws Exception {
		String request = "{\"isbn\":\"" + SECRET_ISBN + "\"}";
		perform(post(BASE + "/books").contentType(MediaType.APPLICATION_JSON).content(request))
			.andExpect(status().isOk());

		String body = assertConflict(post(BASE + "/books").contentType(MediaType.APPLICATION_JSON).content(request),
				CatalogErrorCode.ISBN_ALREADY_EXISTS);

		assertNoLeak(body, output, SECRET_ISBN);
		assertThat(output).contains("constraint=uk_books_isbn, kind=UNIQUE");
	}

	@Test
	void deletingPublisherWithBooksReturnsResourceInUse(CapturedOutput output) throws Exception {
		Publisher publisher = publisherRepository.save(new Publisher("Yayınevi", SECRET_SLUG));
		bookRepository.save(new Book("Kitap", publisher, new BigDecimal("10.00")));

		String body = assertConflict(delete(BASE + "/publishers/" + publisher.getId()), CatalogErrorCode.RESOURCE_IN_USE);

		assertNoLeak(body, output, SECRET_SLUG);
		assertThat(output).contains("constraint=fk_books_publisher, kind=FOREIGN_KEY");
		assertThat(publisherRepository.existsById(publisher.getId())).isTrue();
	}

	@Test
	void reservedAboveStockReturnsGenericConflict(CapturedOutput output) throws Exception {
		String body = assertConflict(post(BASE + "/books/overbooked"), CommonErrorCode.CONFLICT);

		assertNoLeak(body, output);
		assertThat(output).contains("-> CONFLICT").contains("kind=CHECK").contains("constraint=ck_books_");
	}

	@Test
	void staleVersionReturnsConcurrentModification(CapturedOutput output) throws Exception {
		Publisher publisher = publisherRepository.save(new Publisher("Yayınevi", "yayinevi"));
		UUID bookId = bookRepository.save(new Book("Kitap", publisher, new BigDecimal("10.00"))).getId();

		String body = assertConflict(post(BASE + "/books/" + bookId + "/stale-update"),
				CatalogErrorCode.CONCURRENT_MODIFICATION);

		assertNoLeak(body, output);
		assertThat(output).doesNotContain("version=?");
		assertThat(output).contains("POST " + BASE + "/books/" + bookId + "/stale-update -> CONCURRENT_MODIFICATION");
		assertThat(bookRepository.findById(bookId).orElseThrow().getTitle()).isEqualTo("İlk");
	}

	@Test
	void validationErrorsListFieldsWithoutSubmittedValues(CapturedOutput output) throws Exception {
		String body = perform(post(BASE + "/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"cok-uzun-bir-isim-9182\",\"slug\":\"Gecersiz Slug 5523\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.detail").value(CommonErrorCode.VALIDATION_FAILED.defaultDetail()))
			.andExpect(jsonPath("$.instance").value(BASE + "/validate"))
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "slug")))
			.andExpect(jsonPath("$.errors[0].message").isNotEmpty())
			.andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist())
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("9182").doesNotContain("5523");
		assertThat(output).contains("-> VALIDATION_FAILED").doesNotContain("9182").doesNotContain("5523");
	}

	@Test
	void malformedJsonReturnsMalformedRequestWithoutParserDetails() throws Exception {
		String body = perform(post(BASE + "/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"a\", \"slug\": "))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
			.andExpect(jsonPath("$.detail").value(CommonErrorCode.MALFORMED_REQUEST.defaultDetail()))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("com.").doesNotContain("tools.jackson").doesNotContain("Exception");
	}

	@Test
	void unsupportedContentTypeReturns415() throws Exception {
		perform(post(BASE + "/validate").contentType(MediaType.TEXT_PLAIN).content("name=a"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void wrongMethodReturns405WithAllowHeader() throws Exception {
		perform(put(BASE + "/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.ALLOW, "POST"))
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void unknownPathWithTokenReturns404() throws Exception {
		perform(get("/api/other/olmayan-yol"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.instance").value("/api/other/olmayan-yol"));
	}

	@Test
	void unexpectedExceptionReturnsGeneric500(CapturedOutput output) throws Exception {
		String body = perform(get(BASE + "/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.detail").value(CommonErrorCode.INTERNAL_ERROR.defaultDetail()))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("gizli-ic-detay-4411").doesNotContain("IllegalStateException")
			.doesNotContain("at com.").doesNotContain("trace");
		// Stack trace yalnızca sunucu logunda, ERROR seviyesinde.
		assertThat(output).contains("ERROR").contains("-> INTERNAL_ERROR")
			.contains("java.lang.IllegalStateException: gizli-ic-detay-4411");
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.with(bearer(TestJwt.user(SUBJECT))));
	}

	private String assertConflict(MockHttpServletRequestBuilder request, ErrorCode code) throws Exception {
		return perform(request)
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.code").value(code.name()))
			.andExpect(jsonPath("$.detail").value(code.defaultDetail()))
			.andReturn().getResponse().getContentAsString();
	}

	/** Yanıtta SQL/kısıt/stack trace yok; verilen değerler ne yanıtta ne logda geçer. */
	private static void assertNoLeak(String body, CapturedOutput output, String... values) {
		assertThat(body).doesNotContainIgnoringCase("sql")
			.doesNotContainIgnoringCase("constraint")
			.doesNotContain("Duplicate")
			.doesNotContain("insert into").doesNotContain("update books").doesNotContain("delete from")
			.doesNotContain("Exception")
			.doesNotContain("at com.")
			.doesNotContain("uk_").doesNotContain("fk_").doesNotContain("ck_");
		assertThat(output).doesNotContain("Duplicate entry").doesNotContain("Cannot delete or update")
			.doesNotContain("Check constraint");
		for (String value : values) {
			assertThat(body).doesNotContain(value);
			assertThat(output).doesNotContain(value);
		}
	}

}
