package com.kitapsepeti.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.repository.AuthorRepository;
import com.kitapsepeti.catalog.repository.CategoryRepository;
import com.kitapsepeti.catalog.storage.CoverStorage;
import com.kitapsepeti.catalog.storage.InMemoryCoverStorage;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Create + allowlist kapak URL → InMemory depolama; cover_url S3_PUBLIC_BASE_URL host'u taşır.
 */
class CoverIngestIT extends ApiTestSupport {

	private static final byte[] TINY_JPEG = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9 };

	private static final String ADMIN = TestJwt.admin(SUBJECT);

	private static WireMockServer wireMock;

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private CoverStorage coverStorage;

	@Autowired
	private JsonMapper jsonMapper;

	private Author author;

	private Category category;

	@BeforeAll
	static void startWireMock() {
		wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
		wireMock.start();
	}

	@AfterAll
	static void stopWireMock() {
		if (wireMock != null) {
			wireMock.stop();
		}
	}

	@BeforeEach
	void refs() {
		this.author = this.authorRepository.save(new Author("Ingest Yazar", "ingest-yazar"));
		this.category = this.categoryRepository.save(new Category(null, "Ingest", "ingest"));
	}

	@Test
	void createBookIngestsAllowlistedCoverIntoStorage() throws Exception {
		assertThat(this.coverStorage).isInstanceOf(InMemoryCoverStorage.class);
		wireMock.stubFor(get(urlEqualTo("/b/id/42-L.jpg")).willReturn(aResponse().withStatus(200)
			.withHeader("Content-Type", "image/jpeg")
			.withBody(TINY_JPEG)));

		String coverUrl = "http://127.0.0.1:" + wireMock.port() + "/b/id/42-L.jpg";
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", "Ingest Kapak");
		body.put("priceAmount", new BigDecimal("10.00"));
		body.put("authorNames", List.of(this.author.getName()));
		body.put("categoryIds", List.of(this.category.getId()));
		body.put("coverUrl", coverUrl);

		MvcResult result = mockMvc.perform(post("/api/admin/books").with(request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN);
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content(this.jsonMapper.writeValueAsString(body)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.coverUrl", startsWith("http://localhost:9000/kitapsepeti-covers/")))
			.andReturn();

		String stored = JsonPath.read(result.getResponse().getContentAsString(), "$.coverUrl");
		UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
		assertThat(jdbc.queryForObject("SELECT cover_url FROM books WHERE id = UUID_TO_BIN(?)", String.class,
				id.toString())).isEqualTo(stored);
		assertThat(stored).doesNotContain("127.0.0.1");
		assertThat(((InMemoryCoverStorage) this.coverStorage).size()).isEqualTo(1);
	}

}
