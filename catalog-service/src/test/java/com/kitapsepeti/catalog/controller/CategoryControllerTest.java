package com.kitapsepeti.catalog.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class CategoryControllerTest extends ApiTestSupport {

	@Autowired
	private CategoryRepository categoryRepository;

	@Test
	void listIsFlatAndSortedByTurkishName() throws Exception {
		categoryRepository.save(new Category("Edebiyat", "edebiyat"));
		categoryRepository.save(new Category("Çocuk", "cocuk"));
		categoryRepository.save(new Category("Bilim", "bilim"));
		categoryRepository.save(new Category("Roman", "roman"));

		mockMvc.perform(get("/api/categories"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$[*].name", contains("Bilim", "Çocuk", "Edebiyat", "Roman")))
			.andExpect(jsonPath("$[0].children").doesNotExist())
			.andExpect(jsonPath("$[0].slug").value("bilim"));
	}

	@Test
	void emptyCatalogReturnsEmptyArray() throws Exception {
		mockMvc.perform(get("/api/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

}
