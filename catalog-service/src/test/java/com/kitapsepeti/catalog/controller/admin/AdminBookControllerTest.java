package com.kitapsepeti.catalog.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.entity.Publisher;
import com.kitapsepeti.catalog.repository.AuthorRepository;
import com.kitapsepeti.catalog.repository.CategoryRepository;
import com.kitapsepeti.catalog.repository.PublisherRepository;
import com.kitapsepeti.catalog.support.SqlCapture;
import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Admin kitap uçları: CRUD, yayın/arşiv, stok düzeltme ve outbox olayları. */
class AdminBookControllerTest extends ApiTestSupport {

	private static final String BASE = "/api/admin/books";

	private static final String ADMIN = TestJwt.admin(SUBJECT);

	private static final String USER = TestJwt.user(SUBJECT);

	private static final String VALID_ISBN13 = "9786053600770";

	@Autowired
	private PublisherRepository publisherRepository;

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private JsonMapper jsonMapper;

	private Publisher publisher;

	private Author ahmet;

	private Author zeynep;

	private Category edebiyat;

	private Category roman;

	@BeforeEach
	void createReferences() {
		publisher = publisherRepository.save(new Publisher("Deniz Yayınları", "deniz-yayinlari"));
		zeynep = authorRepository.save(new Author("Zeynep Yazar", "zeynep-yazar"));
		ahmet = authorRepository.save(new Author("Ahmet Yazar", "ahmet-yazar"));
		edebiyat = categoryRepository.save(new Category(null, "Edebiyat", "edebiyat"));
		roman = categoryRepository.save(new Category(edebiyat, "Roman", "roman"));
		categoryRepository.save(new Category(null, "Bilim", "bilim"));
	}

	// --- 1. Yetki

	@Test
	void requiresAdminRole() throws Exception {
		UUID id = create(validBook());

		mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(BASE + "/" + id + "/publish")).andExpect(status().isUnauthorized());
		mockMvc.perform(get(BASE).with(bearer(USER))).andExpect(status().isForbidden());
		mockMvc.perform(get(BASE + "/" + id).with(bearer(USER))).andExpect(status().isForbidden());
		mockMvc.perform(post(BASE + "/" + id + "/publish").with(bearer(USER))).andExpect(status().isForbidden());
		mockMvc.perform(delete(BASE + "/" + id).with(bearer(USER))).andExpect(status().isForbidden());
		mockMvc.perform(sendAs(USER, post(BASE + "/" + id + "/stock-adjustments"), Map.of("delta", 5)))
			.andExpect(status().isForbidden());
		mockMvc.perform(sendAs(USER, post(BASE), validBook())).andExpect(status().isForbidden());

		mockMvc.perform(get(BASE).with(bearer(ADMIN))).andExpect(status().isOk());
		mockMvc.perform(get(BASE + "/" + id).with(bearer(ADMIN))).andExpect(status().isOk());
		assertThat(statusOf(id)).isEqualTo("draft");
		assertThat(outbox()).isEmpty();
	}

	// --- 2. Oluşturma

	@Test
	void createReturnsDraftWithLocationAndWritesNoEvent() throws Exception {
		Map<String, Object> body = validBook();
		body.put("description", "Açıklama");
		body.put("pageCount", 180);
		body.put("coverUrl", "https://cdn.example.com/kapak.jpg");
		body.put("initialStock", 7);
		body.put("authorNames", List.of(zeynep.getName(), ahmet.getName()));
		body.put("currency", "USD");
		body.put("status", "published");

		MvcResult result = mockMvc.perform(send(post(BASE), body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("draft"))
			.andExpect(jsonPath("$.currency").value("TRY"))
			.andExpect(jsonPath("$.priceAmount").value(149.90))
			.andExpect(jsonPath("$.stockQuantity").value(7))
			.andExpect(jsonPath("$.reservedQuantity").value(0))
			.andExpect(jsonPath("$.availableQuantity").value(7))
			.andExpect(jsonPath("$.version").value(0))
			.andExpect(jsonPath("$.publishedAt").value(nullValue()))
			.andExpect(jsonPath("$.publisher.slug").value("deniz-yayinlari"))
			.andExpect(jsonPath("$.authors[*].name", contains("Ahmet Yazar", "Zeynep Yazar")))
			.andExpect(jsonPath("$.categories[*].slug", contains("roman")))
			.andExpect(jsonPath("$.pageCount").value(180))
			.andExpect(jsonPath("$.createdAt").value(notNullValue()))
			.andReturn();
		String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

		assertThat(result.getResponse().getHeader("Location")).isEqualTo(BASE + "/" + id);
		assertThat(statusOf(UUID.fromString(id))).isEqualTo("draft");
		assertThat(outbox()).isEmpty();
		assertThat(authorRepository.findAll()).hasSize(2);
	}

	@Test
	void createFindsOrCreatesAuthorsByNameWithoutRenamingExisting() throws Exception {
		long authorsBefore = authorRepository.count();
		Map<String, Object> body = validBook();
		body.put("authorNames", List.of("ahmet yazar", "Yeni Yazar"));

		MvcResult result = mockMvc.perform(send(post(BASE), body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.authors[*].name", containsInAnyOrder("Ahmet Yazar", "Yeni Yazar")))
			.andReturn();

		assertThat(authorRepository.count()).isEqualTo(authorsBefore + 1);
		assertThat(authorRepository.findById(ahmet.getId())).get().extracting(Author::getName).isEqualTo("Ahmet Yazar");
		String bookId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_authors WHERE book_id = UUID_TO_BIN(?)", Integer.class,
				bookId)).isEqualTo(2);
	}

	@Test
	void createRejectsMissingOrUnknownReferences() throws Exception {
		Map<String, Object> missingPublisher = validBook();
		missingPublisher.remove("publisherName");
		assertInvalidField(send(post(BASE), missingPublisher), "publisherName");

		Map<String, Object> blankPublisher = validBook();
		blankPublisher.put("publisherName", "   ");
		assertInvalidField(send(post(BASE), blankPublisher), "publisherName");

		UUID unknown = UUID.randomUUID();
		Map<String, Object> unknownCategory = validBook();
		unknownCategory.put("categoryIds", List.of(unknown));
		assertThat(assertInvalidField(send(post(BASE), unknownCategory), "categoryIds"))
			.doesNotContain(unknown.toString());

		Map<String, Object> tooManyAuthors = validBook();
		tooManyAuthors.put("authorNames", IntStream.range(0, 21).mapToObj(i -> "Yazar " + i).toList());
		assertInvalidField(send(post(BASE), tooManyAuthors), "authorNames");

		Map<String, Object> blankAuthorName = validBook();
		blankAuthorName.put("authorNames", List.of("Ahmet", "   "));
		mockMvc.perform(send(post(BASE), blankAuthorName))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.authors.length()").value(1))
			.andExpect(jsonPath("$.authors[0].name").value("Ahmet"));

		Map<String, Object> missingTitle = validBook();
		missingTitle.put("title", "   ");
		assertInvalidField(send(post(BASE), missingTitle), "title");

		Map<String, Object> missingPrice = validBook();
		missingPrice.remove("priceAmount");
		assertInvalidField(send(post(BASE), missingPrice), "priceAmount");
	}

	// --- 3. ISBN

	@Test
	void isbnIsNormalizedValidatedAndUnique() throws Exception {
		Map<String, Object> isbn13 = validBook();
		isbn13.put("isbn", "978-605-360-077-0");
		UUID first = create(isbn13);
		assertThat(isbnOf(first)).isEqualTo(VALID_ISBN13);

		Map<String, Object> isbn10 = validBook();
		isbn10.put("isbn", "0-8044-2957-x");
		mockMvc.perform(send(post(BASE), isbn10))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.isbn").value("080442957X"));

		Map<String, Object> badChecksum = validBook();
		badChecksum.put("isbn", "978-605-360-077-1");
		assertThat(assertInvalidField(send(post(BASE), badChecksum), "isbn")).doesNotContain("077-1")
			.doesNotContain("9786053600771");

		Map<String, Object> duplicate = validBook();
		duplicate.put("isbn", "978 605 360 077 0");
		String body = mockMvc.perform(send(post(BASE), duplicate))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ISBN_ALREADY_EXISTS"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain(VALID_ISBN13).doesNotContain("978 605");
		assertThat(bookCount()).isEqualTo(2);
	}

	// --- 4. Fiyat ve kapak URL'si

	@Test
	void rejectsInvalidPriceCoverUrlPageCountAndStock() throws Exception {
		Map<String, Object> threeDecimals = validBook();
		threeDecimals.put("priceAmount", new BigDecimal("10.999"));
		assertInvalidField(send(post(BASE), threeDecimals), "priceAmount");

		Map<String, Object> negative = validBook();
		negative.put("priceAmount", new BigDecimal("-1.00"));
		assertInvalidField(send(post(BASE), negative), "priceAmount");

		Map<String, Object> ftp = validBook();
		ftp.put("coverUrl", "ftp://cdn.example.com/kapak.jpg");
		assertInvalidField(send(post(BASE), ftp), "coverUrl");

		Map<String, Object> zeroPages = validBook();
		zeroPages.put("pageCount", 0);
		assertInvalidField(send(post(BASE), zeroPages), "pageCount");

		Map<String, Object> negativeStock = validBook();
		negativeStock.put("initialStock", -1);
		assertInvalidField(send(post(BASE), negativeStock), "initialStock");

		Map<String, Object> hugeStock = validBook();
		hugeStock.put("initialStock", 1_000_001);
		assertInvalidField(send(post(BASE), hugeStock), "initialStock");

		assertThat(bookCount()).isZero();

		Map<String, Object> maxStock = validBook();
		maxStock.put("initialStock", 1_000_000);
		assertThat(stockOf(create(maxStock))).isEqualTo(1_000_000);
	}

	// --- 5. Yayınlama

	@Test
	void publishRequiresAuthorsCategoriesAndPositivePrice() throws Exception {
		Map<String, Object> noAuthors = validBook();
		noAuthors.remove("authorNames");
		assertNotPublishable(create(noAuthors), "at least one author", "category", "price");

		Map<String, Object> noCategories = validBook();
		noCategories.put("categoryIds", List.of());
		assertNotPublishable(create(noCategories), "at least one category", "author", "price");

		Map<String, Object> freeBook = validBook();
		freeBook.put("priceAmount", BigDecimal.ZERO);
		assertNotPublishable(create(freeBook), "a price greater than zero", "author", "category");

		Map<String, Object> nothing = validBook();
		nothing.remove("authorNames");
		nothing.remove("categoryIds");
		nothing.put("priceAmount", BigDecimal.ZERO);
		UUID empty = create(nothing);
		mockMvc.perform(post(BASE + "/" + empty + "/publish").with(bearer(ADMIN)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("author")))
			.andExpect(jsonPath("$.detail", containsString("category")))
			.andExpect(jsonPath("$.detail", containsString("price")));

		assertThat(outbox()).isEmpty();
	}

	@Test
	void publishWritesOneUpsertedEventWithSearchPayload() throws Exception {
		Map<String, Object> body = validBook();
		body.put("isbn", "978-605-360-077-0");
		body.put("description", "Açıklama");
		body.put("pageCount", 180);
		body.put("coverUrl", "https://cdn.example.com/kapak.jpg");
		body.put("initialStock", 3);
		body.put("authorNames", List.of(zeynep.getName(), ahmet.getName()));
		UUID id = create(body);

		publish(id).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("published"))
			.andExpect(jsonPath("$.publishedAt").value(notNullValue()))
			.andExpect(jsonPath("$.version").value(1));

		List<Map<String, Object>> rows = outbox();
		assertThat(rows).hasSize(1);
		Map<String, Object> row = rows.get(0);
		assertThat(row.get("aggregate_type")).isEqualTo("book");
		assertThat(row.get("aggregate_id")).isEqualTo(id.toString());
		assertThat(row.get("event_type")).isEqualTo("BookUpserted");
		assertThat(row.get("published_at")).isNull();

		Map<String, Object> payload = payload(row);
		assertThat(payload).containsOnlyKeys("eventVersion", "bookId", "title", "isbn", "description", "priceAmount",
				"currency", "coverUrl", "pageCount", "inStock", "publishedAt", "publisher", "authors", "categories",
				"categoryIdsWithAncestors", "occurredAt");
		assertThat(payload).doesNotContainKeys("stockQuantity", "reservedQuantity", "availableQuantity", "version",
				"status");
		assertThat(payload.get("eventVersion")).isEqualTo(1);
		assertThat(payload.get("bookId")).isEqualTo(id.toString());
		assertThat(payload.get("title")).isEqualTo("Kırmızı Pazartesi");
		assertThat(payload.get("isbn")).isEqualTo(VALID_ISBN13);
		assertThat(payload.get("priceAmount")).isEqualTo("149.90");
		assertThat(payload.get("currency")).isEqualTo("TRY");
		assertThat(payload.get("pageCount")).isEqualTo(180);
		assertThat(payload.get("inStock")).isEqualTo(true);
		assertThat(payload.get("publishedAt")).isNotNull();
		assertThat(payload.get("occurredAt")).isNotNull();
		assertThat(payload.get("publisher")).isEqualTo(Map.of("id", publisher.getId().toString(), "name",
				"Deniz Yayınları", "slug", "deniz-yayinlari"));
		assertThat(JsonPath.<List<String>>read(row.get("payload").toString(), "$.authors[*].slug"))
			.containsExactly("ahmet-yazar", "zeynep-yazar");
		assertThat(JsonPath.<List<String>>read(row.get("payload").toString(), "$.categories[*].slug"))
			.containsExactly("roman");
		assertThat(JsonPath.<List<String>>read(row.get("payload").toString(), "$.categoryIdsWithAncestors"))
			.containsExactlyInAnyOrder(roman.getId().toString(), edebiyat.getId().toString());

		mockMvc.perform(get("/api/books/" + id)).andExpect(status().isOk());
	}

	// --- 6. Tekrar yayınlama

	@Test
	void republishingPublishedBookChangesNothing() throws Exception {
		UUID id = createPublished(1);
		String publishedAt = publishedAtOf(id);
		long version = versionOf(id);

		publish(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("published"));

		assertThat(outbox()).hasSize(1);
		assertThat(publishedAtOf(id)).isEqualTo(publishedAt);
		assertThat(versionOf(id)).isEqualTo(version);
	}

	// --- 7. PATCH

	@Test
	void patchRequiresCurrentVersion() throws Exception {
		UUID id = create(validBook());

		String body = mockMvc.perform(send(patch(BASE + "/" + id), Map.of("version", 5, "title", "Bayat Başlık")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("Bayat");
		assertThat(titleOf(id)).isEqualTo("Kırmızı Pazartesi");
		assertThat(versionOf(id)).isZero();

		assertInvalidField(send(patch(BASE + "/" + id), Map.of("title", "Versiyonsuz")), "version");

		mockMvc.perform(send(patch(BASE + "/" + id), Map.of("version", 0, "title", "  Yeni Başlık  ")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Yeni Başlık"))
			.andExpect(jsonPath("$.version").value(1));
		assertThat(versionOf(id)).isEqualTo(1);
		assertThat(outbox()).isEmpty();
	}

	@Test
	void patchOnPublishedBookEmitsEventReplacesSetsAndIgnoresStockFields() throws Exception {
		Map<String, Object> body = validBook();
		body.put("authorNames", List.of(ahmet.getName(), zeynep.getName()));
		body.put("initialStock", 4);
		UUID id = createBookAndPublish(body);
		long version = versionOf(id);

		Map<String, Object> patch = new LinkedHashMap<>();
		patch.put("version", version);
		patch.put("authorNames", List.of(zeynep.getName()));
		patch.put("priceAmount", new BigDecimal("99.5"));
		patch.put("stockQuantity", 999);
		patch.put("reservedQuantity", 3);
		patch.put("status", "draft");
		mockMvc.perform(send(patch(BASE + "/" + id), patch))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authors[*].slug", contains("zeynep-yazar")))
			.andExpect(jsonPath("$.priceAmount").value(99.50))
			.andExpect(jsonPath("$.stockQuantity").value(4))
			.andExpect(jsonPath("$.reservedQuantity").value(0))
			.andExpect(jsonPath("$.status").value("published"))
			.andExpect(jsonPath("$.version").value(version + 1));

		List<Map<String, Object>> rows = outbox();
		assertThat(rows).extracting(row -> row.get("event_type")).containsExactly("BookUpserted", "BookUpserted");
		String latest = rows.get(1).get("payload").toString();
		assertThat(JsonPath.<List<String>>read(latest, "$.authors[*].slug")).containsExactly("zeynep-yazar");
		assertThat(JsonPath.<String>read(latest, "$.priceAmount")).isEqualTo("99.50");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_authors WHERE book_id = UUID_TO_BIN(?)",
				Integer.class, id.toString())).isEqualTo(1);
	}

	@Test
	void patchClearsOptionalTextFieldsWithEmptyString() throws Exception {
		Map<String, Object> body = validBook();
		body.put("isbn", VALID_ISBN13);
		body.put("description", "Açıklama");
		body.put("coverUrl", "https://cdn.example.com/kapak.jpg");
		body.put("pageCount", 100);
		UUID id = create(body);

		mockMvc.perform(send(patch(BASE + "/" + id),
				Map.of("version", 0, "isbn", "", "description", "", "coverUrl", "")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isbn").value(nullValue()))
			.andExpect(jsonPath("$.description").value(nullValue()))
			.andExpect(jsonPath("$.coverUrl").value(nullValue()))
			.andExpect(jsonPath("$.pageCount").value(100))
			.andExpect(jsonPath("$.title").value("Kırmızı Pazartesi"));
		assertThat(isbnOf(id)).isNull();

		assertInvalidField(send(patch(BASE + "/" + id), Map.of("version", 1, "title", "")), "title");
	}

	// --- 8. Arşivleme

	@Test
	void archivingPublishedBookEmitsRemovedAndHidesItFromPublicEndpoints() throws Exception {
		UUID id = createPublished(1);
		mockMvc.perform(get("/api/books/" + id)).andExpect(status().isOk());

		mockMvc.perform(post(BASE + "/" + id + "/archive").with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("archived"))
			.andExpect(jsonPath("$.publishedAt").value(notNullValue()));

		List<Map<String, Object>> rows = outbox();
		assertThat(rows).extracting(row -> row.get("event_type")).containsExactly("BookUpserted", "BookRemoved");
		Map<String, Object> removed = payload(rows.get(1));
		assertThat(removed).containsOnlyKeys("eventVersion", "bookId", "occurredAt");
		assertThat(removed.get("bookId")).isEqualTo(id.toString());
		assertThat(rows.get(1).get("aggregate_type")).isEqualTo("book");

		mockMvc.perform(get("/api/books/" + id)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/books")).andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(post(BASE + "/" + id + "/archive").with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("archived"));
		assertThat(outbox()).hasSize(2);
	}

	@Test
	void archivingDraftWritesNoEvent() throws Exception {
		UUID id = create(validBook());

		mockMvc.perform(post(BASE + "/" + id + "/archive").with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("archived"));

		assertThat(outbox()).isEmpty();
	}

	@Test
	void deleteArchivesWithoutRemovingRowAndRepublishKeepsOriginalPublishedAt() throws Exception {
		UUID id = createPublished(1);
		String firstPublishedAt = publishedAtOf(id);

		mockMvc.perform(delete(BASE + "/" + id).with(bearer(ADMIN))).andExpect(status().isNoContent());

		assertThat(statusOf(id)).isEqualTo("archived");
		assertThat(bookCount()).isEqualTo(1);
		assertThat(outbox()).extracting(row -> row.get("event_type")).containsExactly("BookUpserted", "BookRemoved");

		publish(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("published"));
		assertThat(publishedAtOf(id)).isEqualTo(firstPublishedAt);
		assertThat(outbox()).extracting(row -> row.get("event_type"))
			.containsExactly("BookUpserted", "BookRemoved", "BookUpserted");

		mockMvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(bearer(ADMIN))).andExpect(status().isNotFound());
	}

	// --- 9. Stok düzeltme

	@Test
	void stockAdjustmentIsConditionalOnReservedQuantity() throws Exception {
		UUID id = create(validBook());

		adjust(id, Map.of("delta", 5)).andExpect(status().isOk())
			.andExpect(jsonPath("$.stockQuantity").value(5))
			.andExpect(jsonPath("$.availableQuantity").value(5))
			.andExpect(jsonPath("$.version").value(0));

		setReserved(id, 4);
		adjust(id, Map.of("delta", -2)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STOCK_BELOW_RESERVED"));
		assertThat(stockOf(id)).isEqualTo(5);

		adjust(id, Map.of("delta", -1)).andExpect(status().isOk())
			.andExpect(jsonPath("$.stockQuantity").value(4))
			.andExpect(jsonPath("$.reservedQuantity").value(4))
			.andExpect(jsonPath("$.availableQuantity").value(0));

		adjust(UUID.randomUUID(), Map.of("delta", 1)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		assertInvalidField(send(post(BASE + "/" + id + "/stock-adjustments"), Map.of("delta", 0)), "delta");
		assertInvalidField(send(post(BASE + "/" + id + "/stock-adjustments"), Map.of("delta", 100_001)), "delta");
		assertInvalidField(send(post(BASE + "/" + id + "/stock-adjustments"), Map.of("delta", -100_001)), "delta");
		assertInvalidField(send(post(BASE + "/" + id + "/stock-adjustments"), Map.of()), "delta");
		assertThat(stockOf(id)).isEqualTo(4);
		assertThat(outbox()).isEmpty();
	}

	// --- 10. inStock geçişleri

	@Test
	void stockAdjustmentEmitsEventOnlyWhenInStockChangesOnPublishedBook() throws Exception {
		UUID id = createPublished(0);
		assertThat(outbox()).hasSize(1);
		assertThat(payload(outbox().get(0)).get("inStock")).isEqualTo(false);

		adjust(id, Map.of("delta", 3)).andExpect(status().isOk());
		assertThat(outbox()).hasSize(2);
		assertThat(payload(outbox().get(1)).get("inStock")).isEqualTo(true);

		adjust(id, Map.of("delta", 2)).andExpect(status().isOk()).andExpect(jsonPath("$.stockQuantity").value(5));
		assertThat(outbox()).hasSize(2);

		adjust(id, Map.of("delta", -5)).andExpect(status().isOk()).andExpect(jsonPath("$.stockQuantity").value(0));
		assertThat(outbox()).hasSize(3);
		Map<String, Object> last = payload(outbox().get(2));
		assertThat(last.get("inStock")).isEqualTo(false);
		assertThat(last).doesNotContainKeys("stockQuantity", "reservedQuantity");
	}

	// --- 12. Outbox atomikliği

	@Test
	void eventIsRolledBackTogetherWithBookWhenFlushFails() throws Exception {
		Map<String, Object> holder = validBook();
		holder.put("isbn", VALID_ISBN13);
		create(holder);
		UUID id = createPublished(1);
		long version = versionOf(id);
		jdbc.update("DELETE FROM outbox");

		SqlCapture.start();
		String body = mockMvc.perform(send(patch(BASE + "/" + id),
				Map.of("version", version, "title", "Değişmiş Başlık", "isbn", "978-605-360-077-0")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ISBN_ALREADY_EXISTS"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> statements = SqlCapture.stop();

		// Olay satırı gerçekten INSERT edildi, ardından books UPDATE'i UNIQUE ihlaliyle düştü ve ikisi geri alındı.
		assertThat(statements).anyMatch(sql -> sql.startsWith("insert into outbox"));
		assertThat(statements).anyMatch(sql -> sql.startsWith("update books"));
		assertThat(outbox()).isEmpty();
		assertThat(titleOf(id)).isEqualTo("Kırmızı Pazartesi");
		assertThat(isbnOf(id)).isNull();
		assertThat(versionOf(id)).isEqualTo(version);
		assertThat(body).doesNotContain("Değişmiş").doesNotContain(VALID_ISBN13).doesNotContain("077-0");
	}

	// --- 13. Admin yanıtları stok ve versiyon içerir; public yanıtlar içermez

	@Test
	void adminResponsesExposeStockAndVersionButPublicOnesDoNot() throws Exception {
		UUID id = createPublished(3);
		setReserved(id, 1);

		mockMvc.perform(get(BASE + "/" + id).with(bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stockQuantity").value(3))
			.andExpect(jsonPath("$.reservedQuantity").value(1))
			.andExpect(jsonPath("$.availableQuantity").value(2))
			.andExpect(jsonPath("$.version").value(1))
			.andExpect(jsonPath("$.status").value("published"))
			.andExpect(jsonPath("$.updatedAt").value(notNullValue()));
		mockMvc.perform(get(BASE).with(bearer(ADMIN)))
			.andExpect(jsonPath("$.items[0].id").value(id.toString()))
			.andExpect(jsonPath("$.items[0].stockQuantity").value(3))
			.andExpect(jsonPath("$.items[0].reservedQuantity").value(1))
			.andExpect(jsonPath("$.items[0].availableQuantity").value(2))
			.andExpect(jsonPath("$.items[0].version").value(1))
			.andExpect(jsonPath("$.items[0].publisher.slug").value("deniz-yayinlari"));

		for (String path : List.of("/api/books/" + id, "/api/books")) {
			String body = mockMvc.perform(get(path))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(body).doesNotContain("stockQuantity", "reservedQuantity", "availableQuantity", "\"version\"",
					"\"status\"");
		}
	}

	@Test
	void listFiltersByStatusAndOrdersByLastUpdate() throws Exception {
		UUID older = create(validBook());
		UUID newer = create(validBook());
		UUID published = createPublished(1);
		mockMvc.perform(send(patch(BASE + "/" + older), Map.of("version", 0, "title", "Güncellenen")))
			.andExpect(status().isOk());

		mockMvc.perform(get(BASE).with(bearer(ADMIN)))
			.andExpect(jsonPath("$.items[*].id", contains(older.toString(), published.toString(), newer.toString())))
			.andExpect(jsonPath("$.totalElements").value(3));
		mockMvc.perform(get(BASE + "?status=draft").with(bearer(ADMIN)))
			.andExpect(jsonPath("$.items[*].id", contains(older.toString(), newer.toString())))
			.andExpect(jsonPath("$.items[*].status", contains("draft", "draft")));
		mockMvc.perform(get(BASE + "?status=PUBLISHED&size=1").with(bearer(ADMIN)))
			.andExpect(jsonPath("$.items[*].id", contains(published.toString())))
			.andExpect(jsonPath("$.size").value(1));
		mockMvc.perform(get(BASE + "?status=archived").with(bearer(ADMIN)))
			.andExpect(jsonPath("$.totalElements").value(0));

		assertInvalidField(get(BASE + "?status=silindi").with(bearer(ADMIN)), "status");
		assertInvalidField(get(BASE + "?size=101").with(bearer(ADMIN)), "size");
		assertInvalidField(get(BASE + "?size=0").with(bearer(ADMIN)), "size");
		mockMvc.perform(get(BASE + "/" + UUID.randomUUID()).with(bearer(ADMIN))).andExpect(status().isNotFound());
	}

	// --- 14. Hata yanıtları gönderilen değerleri içermez

	@Test
	void errorResponsesDoNotEchoSubmittedValues() throws Exception {
		Map<String, Object> body = validBook();
		body.put("title", "GizliBaslik" + "x".repeat(300));
		body.put("isbn", "978-0-306-40615-8");
		body.put("coverUrl", "ftp://gizli-sunucu.example.com/kapak.jpg");
		String response = mockMvc.perform(send(post(BASE), body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "isbn", "coverUrl")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(response).doesNotContain("GizliBaslik", "40615", "gizli-sunucu");

		Map<String, Object> free = validBook();
		free.put("title", "GizliYayin");
		free.put("priceAmount", BigDecimal.ZERO);
		UUID id = create(free);
		mockMvc.perform(post(BASE + "/" + id + "/publish").with(bearer(ADMIN)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", not(containsString("GizliYayin"))))
			.andExpect(jsonPath("$.detail", not(containsString("0.00"))));
	}

	// --- yardımcılar

	private Map<String, Object> validBook() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", "Kırmızı Pazartesi");
		body.put("publisherName", publisher.getName());
		body.put("priceAmount", new BigDecimal("149.90"));
		body.put("authorNames", List.of(ahmet.getName()));
		body.put("categoryIds", List.of(roman.getId()));
		return body;
	}

	private UUID create(Map<String, Object> body) throws Exception {
		MvcResult result = mockMvc.perform(send(post(BASE), body))
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andReturn();
		return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
	}

	private UUID createPublished(int initialStock) throws Exception {
		Map<String, Object> body = validBook();
		body.put("initialStock", initialStock);
		return createBookAndPublish(body);
	}

	private UUID createBookAndPublish(Map<String, Object> body) throws Exception {
		UUID id = create(body);
		publish(id).andExpect(status().isOk());
		return id;
	}

	private ResultActions publish(UUID id) throws Exception {
		return mockMvc.perform(post(BASE + "/" + id + "/publish").with(bearer(ADMIN)));
	}

	private ResultActions adjust(UUID id, Map<String, ?> body) throws Exception {
		return mockMvc.perform(send(post(BASE + "/" + id + "/stock-adjustments"), body));
	}

	private void assertNotPublishable(UUID id, String expected, String... absent) throws Exception {
		ResultActions result = publish(id).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BOOK_NOT_PUBLISHABLE"))
			.andExpect(jsonPath("$.detail", containsString(expected)));
		for (String word : absent) {
			result.andExpect(jsonPath("$.detail", not(containsString(word))));
		}
		assertThat(statusOf(id)).isEqualTo("draft");
	}

	/** 400 VALIDATION_FAILED ve tek alanlı errors; yanıt gövdesini döndürür. */
	private String assertInvalidField(MockHttpServletRequestBuilder request, String field) throws Exception {
		return mockMvc.perform(request)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", contains(field)))
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private MockHttpServletRequestBuilder send(MockHttpServletRequestBuilder request, Object body) {
		return sendAs(ADMIN, request, body);
	}

	private MockHttpServletRequestBuilder sendAs(String token, MockHttpServletRequestBuilder request, Object body) {
		return request.with(bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonMapper.writeValueAsString(body));
	}

	private List<Map<String, Object>> outbox() {
		return jdbc.queryForList("SELECT aggregate_type, BIN_TO_UUID(aggregate_id) AS aggregate_id, event_type, "
				+ "CAST(payload AS CHAR) AS payload, published_at FROM outbox ORDER BY created_at, id");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> payload(Map<String, Object> row) {
		return jsonMapper.readValue(row.get("payload").toString(), Map.class);
	}

	private void setReserved(UUID id, int reserved) {
		jdbc.update("UPDATE books SET reserved_quantity = ? WHERE id = UUID_TO_BIN(?)", reserved, id.toString());
	}

	private int bookCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM books", Integer.class);
	}

	private String statusOf(UUID id) {
		return column("status", id);
	}

	private String titleOf(UUID id) {
		return column("title", id);
	}

	private String isbnOf(UUID id) {
		return column("isbn", id);
	}

	private String publishedAtOf(UUID id) {
		return column("CAST(published_at AS CHAR)", id);
	}

	private long versionOf(UUID id) {
		return Long.parseLong(column("version", id));
	}

	private int stockOf(UUID id) {
		return Integer.parseInt(column("stock_quantity", id));
	}

	private String column(String expression, UUID id) {
		return jdbc.queryForObject("SELECT " + expression + " FROM books WHERE id = UUID_TO_BIN(?)", String.class,
				id.toString());
	}

}
