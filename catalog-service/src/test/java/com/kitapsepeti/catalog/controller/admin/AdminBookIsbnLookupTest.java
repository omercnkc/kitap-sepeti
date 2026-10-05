package com.kitapsepeti.catalog.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * ISBN lookup: Open Library testte kapalı (127.0.0.1:9); geçerli ISBN → 404 BOOK_METADATA_NOT_FOUND.
 * Başarılı map birim testlerinde.
 */
class AdminBookIsbnLookupTest extends ApiTestSupport {

	private static final String PATH = "/api/admin/books/isbn-lookup";

	private static final String ADMIN = TestJwt.admin(SUBJECT);

	private static final String USER = TestJwt.user(SUBJECT);

	private static final String VALID_ISBN13 = "9786053600770";

	@Test
	void requiresAdmin() throws Exception {
		mockMvc.perform(get(PATH).param("isbn", VALID_ISBN13)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(PATH).param("isbn", VALID_ISBN13).with(bearer(USER))).andExpect(status().isForbidden());
	}

	@Test
	void invalidIsbnReturnsValidationFailed() throws Exception {
		mockMvc.perform(get(PATH).param("isbn", "not-an-isbn").with(bearer(ADMIN))
			.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("isbn"));
	}

	@Test
	void unknownOrUnreachableProviderReturnsNotFound() throws Exception {
		mockMvc.perform(get(PATH).param("isbn", VALID_ISBN13).with(bearer(ADMIN))
			.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BOOK_METADATA_NOT_FOUND"));
	}

}
