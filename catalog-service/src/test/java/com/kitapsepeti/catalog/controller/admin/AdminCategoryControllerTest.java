package com.kitapsepeti.catalog.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.repository.BookRepository;
import com.kitapsepeti.catalog.repository.CategoryRepository;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AdminCategoryControllerTest extends ApiTestSupport {

	private static final String BASE = "/api/admin/categories";

	private static final String ADMIN = TestJwt.admin(SUBJECT);

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private BookRepository bookRepository;

	@Test
	void createGetListRenameChangeSlugAndDelete() throws Exception {
		MvcResult created = mockMvc.perform(json(post(BASE), "{\"name\":\"Roman\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value("roman"))
			.andReturn();
		String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
		assertThat(created.getResponse().getHeader("Location")).isEqualTo(BASE + "/" + id);

		mockMvc.perform(get(BASE + "/" + id).with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Roman"))
			.andExpect(jsonPath("$.parentId").doesNotExist());
		mockMvc.perform(get(BASE).with(bearer(ADMIN))).andExpect(jsonPath("$.items[*].id", hasItem(id)));

		mockMvc.perform(json(patch(BASE + "/" + id), "{\"name\":\"Türk Romanı\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Türk Romanı"))
			.andExpect(jsonPath("$.slug").value("roman"));
		mockMvc.perform(json(patch(BASE + "/" + id), "{\"slug\":\"turk-romani\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("turk-romani"));

		mockMvc.perform(delete(BASE + "/" + id).with(bearer(ADMIN))).andExpect(status().isNoContent());
		mockMvc.perform(get(BASE + "/" + id).with(bearer(ADMIN))).andExpect(status().isNotFound());
	}

	@Test
	void duplicateSlugAndInvalidInputAreRejected() throws Exception {
		categoryRepository.save(new Category("Var Olan", "var-olan"));

		String body = mockMvc.perform(json(post(BASE), "{\"name\":\"Var Olan\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SLUG_ALREADY_EXISTS"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("var-olan").doesNotContain("Var Olan");

		mockMvc.perform(json(post(BASE), "{\"name\":\"Kategori\",\"slug\":\"Büyük Harf\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("slug"));
	}

	@Test
	void categoryWithBooksCannotBeDeleted() throws Exception {
		Category category = categoryRepository.save(new Category("Kitaplı", "kitapli"));
		Book book = new Book("Kitap", new BigDecimal("10.00"));
		book.getCategories().add(category);
		bookRepository.save(book);

		mockMvc.perform(delete(BASE + "/" + category.getId()).with(bearer(ADMIN)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

		assertThat(categoryRepository.existsById(category.getId())).isTrue();
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

	@Test
	void listIsSortedByTurkishName() throws Exception {
		categoryRepository.save(new Category("Edebiyat", "edebiyat"));
		categoryRepository.save(new Category("Bilim", "bilim"));
		categoryRepository.save(new Category("Çocuk", "cocuk"));

		mockMvc.perform(get(BASE).with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name", contains("Bilim", "Çocuk", "Edebiyat")));
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.with(bearer(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
