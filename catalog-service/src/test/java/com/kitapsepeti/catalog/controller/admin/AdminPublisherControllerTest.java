package com.kitapsepeti.catalog.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
import com.kitapsepeti.catalog.entity.Publisher;
import com.kitapsepeti.catalog.repository.BookRepository;
import com.kitapsepeti.catalog.repository.PublisherRepository;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AdminPublisherControllerTest extends ApiTestSupport {

	private static final String BASE = "/api/admin/publishers";

	private static final String ADMIN = TestJwt.admin(SUBJECT);

	@Autowired
	private PublisherRepository publisherRepository;

	@Autowired
	private BookRepository bookRepository;

	@Test
	void createGetListRenameChangeSlugAndDelete() throws Exception {
		MvcResult created = mockMvc.perform(json(post(BASE), "{\"name\":\"  Çağdaş Öykü Yayınları  \"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Çağdaş Öykü Yayınları"))
			.andExpect(jsonPath("$.slug").value("cagdas-oyku-yayinlari"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.updatedAt").isNotEmpty())
			.andReturn();
		String id = idOf(created);
		assertThat(created.getResponse().getHeader("Location")).isEqualTo(BASE + "/" + id);

		mockMvc.perform(get(BASE + "/" + id).with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.slug").value("cagdas-oyku-yayinlari"));
		mockMvc.perform(get(BASE).with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id", hasItem(id)));

		mockMvc.perform(json(patch(BASE + "/" + id), "{\"name\":\"Yeni Ad\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Yeni Ad"))
			.andExpect(jsonPath("$.slug").value("cagdas-oyku-yayinlari"));
		mockMvc.perform(json(patch(BASE + "/" + id), "{\"slug\":\"yeni-ad\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Yeni Ad"))
			.andExpect(jsonPath("$.slug").value("yeni-ad"));
		mockMvc.perform(json(patch(BASE + "/" + id), "{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Yeni Ad"))
			.andExpect(jsonPath("$.slug").value("yeni-ad"));

		mockMvc.perform(delete(BASE + "/" + id).with(bearer(ADMIN))).andExpect(status().isNoContent());
		mockMvc.perform(get(BASE + "/" + id).with(bearer(ADMIN)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void explicitSlugIsUsedAsGiven() throws Exception {
		mockMvc.perform(json(post(BASE), "{\"name\":\"!!!\",\"slug\":\"unlem-yayinlari\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value("unlem-yayinlari"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Büyük Harf", "a--b", "-a", "a-", "BUYUK", "" })
	void invalidSlugPatternIs400(String slug) throws Exception {
		assertValidationFailed(json(post(BASE), "{\"name\":\"Yayınevi\",\"slug\":\"" + slug + "\"}"), "slug");
		Publisher existing = publisherRepository.save(new Publisher("Var", "var"));
		assertValidationFailed(json(patch(BASE + "/" + existing.getId()), "{\"slug\":\"" + slug + "\"}"), "slug");
	}

	@Test
	void slugLongerThanColumnIs400() throws Exception {
		assertValidationFailed(json(post(BASE), "{\"name\":\"Yayınevi\",\"slug\":\"" + "a".repeat(161) + "\"}"),
				"slug");
	}

	@Test
	void duplicateSlugIs409AndDoesNotEchoTheSlug() throws Exception {
		String secretSlug = "gizli-slug-7f3a";
		mockMvc.perform(json(post(BASE), "{\"name\":\"Birinci\",\"slug\":\"" + secretSlug + "\"}"))
			.andExpect(status().isCreated());

		String body = assertConflict(json(post(BASE), "{\"name\":\"İkinci\",\"slug\":\"" + secretSlug + "\"}"),
				"SLUG_ALREADY_EXISTS");
		assertThat(body).doesNotContain(secretSlug);

		assertConflict(json(post(BASE), "{\"name\":\"Gizli Slug 7f3a\"}"), "SLUG_ALREADY_EXISTS");

		Publisher other = publisherRepository.save(new Publisher("Başka", "baska"));
		body = assertConflict(json(patch(BASE + "/" + other.getId()), "{\"slug\":\"" + secretSlug + "\"}"),
				"SLUG_ALREADY_EXISTS");
		assertThat(body).doesNotContain(secretSlug);
		assertThat(publisherRepository.findById(other.getId()).orElseThrow().getSlug()).isEqualTo("baska");
	}

	@Test
	void invalidNamesAre400AndDoNotEchoTheName() throws Exception {
		String secretName = "GizliAd" + "x".repeat(154);
		assertThat(secretName).hasSize(161);
		String body = assertValidationFailed(json(post(BASE), "{\"name\":\"" + secretName + "\"}"), "name");
		assertThat(body).doesNotContain("GizliAd");

		assertValidationFailed(json(post(BASE), "{\"name\":\"   \"}"), "name");
		assertValidationFailed(json(post(BASE), "{}"), "name");
		assertValidationFailed(json(post(BASE), "{\"name\":\"!!!\"}"), "slug");

		mockMvc.perform(json(post(BASE), "{\"name\":\"" + "a".repeat(160) + "\"}")).andExpect(status().isCreated());
		mockMvc.perform(json(post(BASE), "{\"name\":\"  " + "b".repeat(160) + "  \"}"))
			.andExpect(status().isCreated());

		Publisher existing = publisherRepository.save(new Publisher("Var", "var"));
		assertValidationFailed(json(patch(BASE + "/" + existing.getId()), "{\"name\":\"  \"}"), "name");
		body = assertValidationFailed(json(patch(BASE + "/" + existing.getId()), "{\"name\":\"" + secretName + "\"}"),
				"name");
		assertThat(body).doesNotContain("GizliAd");
		assertThat(publisherRepository.findById(existing.getId()).orElseThrow().getName()).isEqualTo("Var");
	}

	@Test
	void publisherWithBooksCannotBeDeleted() throws Exception {
		Publisher publisher = publisherRepository.save(new Publisher("Kitaplı", "kitapli"));
		bookRepository.save(new Book("Kitap", publisher, new BigDecimal("10.00")));

		assertConflict(delete(BASE + "/" + publisher.getId()).with(bearer(ADMIN)), "RESOURCE_IN_USE");

		assertThat(publisherRepository.existsById(publisher.getId())).isTrue();
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
	void listIsPagedAndSortedByNameThenId() throws Exception {
		for (String name : List.of("Cem", "Ahmet", "Bora", "Ece", "Deniz")) {
			publisherRepository.save(new Publisher(name, name.toLowerCase()));
		}
		UUID sameNameFirst = publisherRepository.save(new Publisher("Aynı", "ayni-1")).getId();
		UUID sameNameSecond = publisherRepository.save(new Publisher("Aynı", "ayni-2")).getId();
		List<String> sameNameIds = List.of(sameNameFirst.toString(), sameNameSecond.toString())
			.stream()
			.sorted()
			.toList();

		mockMvc.perform(get(BASE + "?page=0&size=3").with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name", contains("Ahmet", "Aynı", "Aynı")))
			.andExpect(jsonPath("$.items[1].id").value(sameNameIds.get(0)))
			.andExpect(jsonPath("$.items[2].id").value(sameNameIds.get(1)))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(3))
			.andExpect(jsonPath("$.totalElements").value(7))
			.andExpect(jsonPath("$.totalPages").value(3));
		mockMvc.perform(get(BASE + "?page=2&size=3").with(bearer(ADMIN)))
			.andExpect(jsonPath("$.items[*].name", contains("Ece")));
		mockMvc.perform(get(BASE + "?page=3&size=3").with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(0)));
		mockMvc.perform(get(BASE).with(bearer(ADMIN)))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.items", hasSize(7)));
		mockMvc.perform(get(BASE + "?size=100").with(bearer(ADMIN))).andExpect(status().isOk());

		assertValidationFailed(get(BASE + "?size=101").with(bearer(ADMIN)), "size");
		assertValidationFailed(get(BASE + "?size=0").with(bearer(ADMIN)), "size");
		assertValidationFailed(get(BASE + "?page=-1").with(bearer(ADMIN)), "page");
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.with(bearer(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private String assertValidationFailed(RequestBuilder request, String field) throws Exception {
		return mockMvc.perform(request)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", contains(field)))
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private String assertConflict(RequestBuilder request, String code) throws Exception {
		return mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(code))
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private static String idOf(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

}
