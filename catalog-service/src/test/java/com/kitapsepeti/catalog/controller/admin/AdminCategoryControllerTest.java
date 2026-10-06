package com.kitapsepeti.catalog.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
	void createsRootAndChildAndRejectsUnknownParent() throws Exception {
		MvcResult rootResult = mockMvc.perform(json(post(BASE), "{\"name\":\"Edebiyat\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.parentId").value(nullValue()))
			.andExpect(jsonPath("$.slug").value("edebiyat"))
			.andReturn();
		String rootId = idOf(rootResult);
		assertThat(rootResult.getResponse().getHeader("Location")).isEqualTo(BASE + "/" + rootId);

		String childBody = "{\"name\":\"Türk Romanı\",\"parentId\":\"" + rootId + "\"}";
		String childId = idOf(mockMvc.perform(json(post(BASE), childBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.parentId").value(rootId))
			.andExpect(jsonPath("$.slug").value("turk-romani"))
			.andReturn());

		mockMvc.perform(get(BASE + "/" + childId).with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").value(rootId));
		mockMvc.perform(get(BASE).with(bearer(ADMIN)))
			.andExpect(jsonPath("$.items[*].name", contains("Edebiyat", "Türk Romanı")))
			.andExpect(jsonPath("$.items[1].parentId").value(rootId));

		String unknownParent = UUID.randomUUID().toString();
		String body = mockMvc.perform(json(post(BASE), "{\"name\":\"Yetim\",\"parentId\":\"" + unknownParent + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", contains("parentId")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain(unknownParent);
		assertThat(categoryRepository.existsBySlug("yetim")).isFalse();

		mockMvc.perform(json(post(BASE), "{\"name\":\"" + "k".repeat(121) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", contains("name")));
		mockMvc.perform(json(post(BASE), "{\"name\":\"Uzun\",\"slug\":\"" + "k".repeat(121) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", contains("slug")));
	}

	@Test
	void patchRenamesWithoutChangingSlugOrParent() throws Exception {
		Category root = categoryRepository.save(new Category(null, "Edebiyat", "edebiyat"));
		Category child = categoryRepository.save(new Category(root, "Roman", "roman"));

		mockMvc.perform(json(patch(BASE + "/" + child.getId()), "{\"name\":\"Romanlar\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Romanlar"))
			.andExpect(jsonPath("$.slug").value("roman"))
			.andExpect(jsonPath("$.parentId").value(root.getId().toString()));
		mockMvc.perform(json(patch(BASE + "/" + child.getId()), "{\"slug\":\"romanlar\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("romanlar"));
		mockMvc.perform(json(patch(BASE + "/" + child.getId()), "{\"slug\":\"edebiyat\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SLUG_ALREADY_EXISTS"));
	}

	@Test
	void moveChangesPositionInPublicTreeAndRejectsCycles() throws Exception {
		Category edebiyat = categoryRepository.save(new Category(null, "Edebiyat", "edebiyat"));
		Category bilim = categoryRepository.save(new Category(null, "Bilim", "bilim"));
		Category roman = categoryRepository.save(new Category(edebiyat, "Roman", "roman"));
		Category polisiye = categoryRepository.save(new Category(roman, "Polisiye", "polisiye"));

		mockMvc.perform(move(roman, bilim.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").value(bilim.getId().toString()));
		mockMvc.perform(get("/api/categories"))
			.andExpect(jsonPath("$[*].name", contains("Bilim", "Edebiyat")))
			.andExpect(jsonPath("$[0].children[*].name", contains("Roman")))
			.andExpect(jsonPath("$[0].children[0].children[*].name", contains("Polisiye")))
			.andExpect(jsonPath("$[1].children").isEmpty());

		mockMvc.perform(move(roman, null))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").value(nullValue()));
		mockMvc.perform(get("/api/categories"))
			.andExpect(jsonPath("$[*].name", contains("Bilim", "Edebiyat", "Roman")))
			.andExpect(jsonPath("$[2].children[*].name", contains("Polisiye")));

		assertCycle(move(roman, roman.getId()));
		assertCycle(move(roman, polisiye.getId()));
		Category kisa = categoryRepository.save(new Category(polisiye, "Kısa Polisiye", "kisa-polisiye"));
		assertCycle(move(roman, kisa.getId()));

		mockMvc.perform(put(BASE + "/" + roman.getId() + "/parent").with(bearer(ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").value(nullValue()));

		mockMvc.perform(move(roman, UUID.randomUUID()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", contains("parentId")));

		assertThat(parentIdOf(roman)).isNull();
		assertThat(parentIdOf(polisiye)).isEqualTo(roman.getId());

		mockMvc.perform(move(polisiye, edebiyat.getId())).andExpect(status().isOk());
		mockMvc.perform(get(BASE).with(bearer(ADMIN)))
			.andExpect(jsonPath("$.items[?(@.name == 'Polisiye')].parentId", contains(edebiyat.getId().toString())))
			.andExpect(jsonPath("$.items[?(@.name == 'Kısa Polisiye')].parentId",
					contains(polisiye.getId().toString())));
	}

	@Test
	void categoryWithChildrenOrBooksCannotBeDeletedButEmptyLeafCan() throws Exception {
		Category parent = categoryRepository.save(new Category(null, "Ana", "ana"));
		categoryRepository.save(new Category(parent, "Alt", "alt"));
		Category withBook = categoryRepository.save(new Category(null, "Kitaplı", "kitapli"));
		Category leaf = categoryRepository.save(new Category(null, "Boş", "bos"));
		Book book = new Book("Kitap", new BigDecimal("10.00"));
		book.getCategories().add(withBook);
		bookRepository.save(book);

		for (Category inUse : List.of(parent, withBook)) {
			mockMvc.perform(delete(BASE + "/" + inUse.getId()).with(bearer(ADMIN)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
			assertThat(categoryRepository.existsById(inUse.getId())).isTrue();
		}

		mockMvc.perform(delete(BASE + "/" + leaf.getId()).with(bearer(ADMIN))).andExpect(status().isNoContent());
		mockMvc.perform(get(BASE + "/" + leaf.getId()).with(bearer(ADMIN))).andExpect(status().isNotFound());
	}

	@Test
	void unknownIdIs404() throws Exception {
		Category existing = categoryRepository.save(new Category(null, "Var", "var"));
		String path = BASE + "/" + UUID.randomUUID();
		List<RequestBuilder> requests = List.of(get(path).with(bearer(ADMIN)), json(patch(path), "{\"name\":\"Ad\"}"),
				delete(path).with(bearer(ADMIN)), json(put(path + "/parent"), "{\"parentId\":null}"),
				json(put(path + "/parent"), "{\"parentId\":\"" + existing.getId() + "\"}"),
				json(put(path + "/parent"), "{\"parentId\":\"" + UUID.randomUUID() + "\"}"));
		for (RequestBuilder request : requests) {
			mockMvc.perform(request)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		}
	}

	@Test
	void flatListIsPagedAndSortedByName() throws Exception {
		Category root = categoryRepository.save(new Category(null, "Edebiyat", "edebiyat"));
		categoryRepository.save(new Category(root, "Deneme", "deneme"));
		categoryRepository.save(new Category(null, "Bilim", "bilim"));

		mockMvc.perform(get(BASE + "?size=2").with(bearer(ADMIN)))
			.andExpect(jsonPath("$.items[*].name", contains("Bilim", "Deneme")))
			.andExpect(jsonPath("$.items[0].parentId").value(nullValue()))
			.andExpect(jsonPath("$.items[1].parentId").value(root.getId().toString()))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(2));
		mockMvc.perform(get(BASE + "?size=101").with(bearer(ADMIN)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", contains("size")));
	}

	private MockHttpServletRequestBuilder move(Category category, UUID parentId) {
		String parent = (parentId != null) ? "\"" + parentId + "\"" : "null";
		return json(put(BASE + "/" + category.getId() + "/parent"), "{\"parentId\":" + parent + "}");
	}

	private void assertCycle(RequestBuilder request) throws Exception {
		mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));
	}

	private UUID parentIdOf(Category category) {
		String id = jdbc.queryForObject("SELECT BIN_TO_UUID(parent_id) FROM categories WHERE id = UUID_TO_BIN(?)",
				String.class, category.getId().toString());
		return (id != null) ? UUID.fromString(id) : null;
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.with(bearer(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static String idOf(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

}
