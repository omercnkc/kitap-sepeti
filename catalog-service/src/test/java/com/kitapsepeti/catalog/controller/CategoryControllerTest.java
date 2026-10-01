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
	void treeIsNestedAndEachLevelSortedByTurkishName() throws Exception {
		Category edebiyat = categoryRepository.save(new Category(null, "Edebiyat", "edebiyat"));
		categoryRepository.save(new Category(null, "Çocuk", "cocuk"));
		Category bilim = categoryRepository.save(new Category(null, "Bilim", "bilim"));
		Category roman = categoryRepository.save(new Category(edebiyat, "Roman", "roman"));
		categoryRepository.save(new Category(edebiyat, "Öykü", "oyku"));
		categoryRepository.save(new Category(edebiyat, "Deneme", "deneme"));
		categoryRepository.save(new Category(roman, "Polisiye", "polisiye"));
		categoryRepository.save(new Category(roman, "Bilimkurgu", "bilimkurgu"));
		categoryRepository.save(new Category(bilim, "Popüler Bilim", "populer-bilim"));

		mockMvc.perform(get("/api/categories"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$[*].name", contains("Bilim", "Çocuk", "Edebiyat")))
			.andExpect(jsonPath("$[0].children[*].slug", contains("populer-bilim")))
			.andExpect(jsonPath("$[1].children", hasSize(0)))
			.andExpect(jsonPath("$[2].id").value(edebiyat.getId().toString()))
			.andExpect(jsonPath("$[2].children[*].name", contains("Deneme", "Öykü", "Roman")))
			.andExpect(jsonPath("$[2].children[2].children[*].name", contains("Bilimkurgu", "Polisiye")))
			.andExpect(jsonPath("$[2].children[2].children[0].children", hasSize(0)));
	}

	@Test
	void emptyCatalogReturnsEmptyArray() throws Exception {
		mockMvc.perform(get("/api/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

}
