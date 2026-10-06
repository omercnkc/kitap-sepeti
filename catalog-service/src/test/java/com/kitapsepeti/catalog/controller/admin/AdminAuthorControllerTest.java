package com.kitapsepeti.catalog.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.repository.AuthorRepository;
import com.kitapsepeti.catalog.repository.BookRepository;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AdminAuthorControllerTest extends ApiTestSupport {

	private static final String BASE = "/api/admin/authors";

	private static final String ADMIN = TestJwt.admin(SUBJECT);

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private BookRepository bookRepository;

	@Test
	void createGetListRenameChangeSlugAndDelete() throws Exception {
		MvcResult created = mockMvc.perform(json(post(BASE), "{\"name\":\"Sabahattin Ali\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value("sabahattin-ali"))
			.andReturn();
		String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
		assertThat(created.getResponse().getHeader("Location")).isEqualTo(BASE + "/" + id);

		mockMvc.perform(get(BASE + "/" + id).with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Sabahattin Ali"));
		mockMvc.perform(get(BASE).with(bearer(ADMIN))).andExpect(jsonPath("$.items[*].id", hasItem(id)));

		mockMvc.perform(json(patch(BASE + "/" + id), "{\"name\":\"S. Ali\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("S. Ali"))
			.andExpect(jsonPath("$.slug").value("sabahattin-ali"));
		mockMvc.perform(json(patch(BASE + "/" + id), "{\"slug\":\"s-ali\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("s-ali"));

		mockMvc.perform(delete(BASE + "/" + id).with(bearer(ADMIN))).andExpect(status().isNoContent());
		mockMvc.perform(get(BASE + "/" + id).with(bearer(ADMIN))).andExpect(status().isNotFound());
	}

	@Test
	void duplicateSlugAndInvalidInputAreRejected() throws Exception {
		authorRepository.save(new Author("Var Olan", "var-olan"));

		String body = mockMvc.perform(json(post(BASE), "{\"name\":\"Var Olan\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SLUG_ALREADY_EXISTS"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("var-olan").doesNotContain("Var Olan");

		mockMvc.perform(json(post(BASE), "{\"name\":\"Yazar\",\"slug\":\"Büyük Harf\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("slug"));
		mockMvc.perform(json(post(BASE), "{\"name\":\"" + "a".repeat(161) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("name"));
	}

	@Test
	void authorWithBooksCannotBeDeleted() throws Exception {
		Author author = authorRepository.save(new Author("Kitaplı Yazar", "kitapli-yazar"));
		Book book = new Book("Kitap", new BigDecimal("10.00"));
		book.getAuthors().add(author);
		bookRepository.save(book);

		mockMvc.perform(delete(BASE + "/" + author.getId()).with(bearer(ADMIN)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

		assertThat(authorRepository.existsById(author.getId())).isTrue();
	}

	@Test
	void unknownIdIs404() throws Exception {
		String path = BASE + "/" + UUID.randomUUID();
		for (RequestBuilder request : List.of(get(path).with(bearer(ADMIN)), json(patch(path), "{\"name\":\"Ad\"}"),
				delete(path).with(bearer(ADMIN)))) {
			mockMvc.perform(request)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		}
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.with(bearer(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
