package com.kitapsepeti.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.BookStatus;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.repository.AuthorRepository;
import com.kitapsepeti.catalog.repository.BookRepository;
import com.kitapsepeti.catalog.repository.CategoryRepository;
import com.kitapsepeti.catalog.support.TestJwt;
import jakarta.persistence.EntityManagerFactory;
import org.hamcrest.Matcher;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Herkese açık kitap uçları; her test kendi verisini kurar (seed kullanılmaz). */
class BookControllerTest extends ApiTestSupport {

	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void listContainsOnlyPublishedBooks() throws Exception {
		save(book("Yayında", "10.00"));
		save(withStatus(book("Taslak", "10.00"), BookStatus.DRAFT));
		save(withStatus(book("Arşiv", "10.00"), BookStatus.ARCHIVED));

		mockMvc.perform(get("/api/books"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.items[*].title", contains("Yayında")))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void authorAndPriceFiltersWorkAloneAndCombined() throws Exception {
		Author x = authorRepository.save(new Author("Yazar X", "yazar-x"));
		Author y = authorRepository.save(new Author("Yazar Y", "yazar-y"));
		save(withAuthors(book("B1", "50.00"), x));
		save(withAuthors(book("B2", "100.00"), y));
		save(withAuthors(book("B3", "150.00"), x));
		save(withAuthors(book("B4", "200.00"), y, x));

		assertTitles("authorId=" + x.getId(), "B1", "B3", "B4");
		assertTitles("minPrice=100", "B2", "B3", "B4");
		assertTitles("maxPrice=100", "B1", "B2");
		assertTitles("minPrice=100&maxPrice=150", "B2", "B3");
		assertTitles("authorId=" + x.getId() + "&maxPrice=180", "B1", "B3");
		assertTitles("authorId=" + UUID.randomUUID());
	}

	@Test
	void titleSearchMatchesCaseInsensitiveAndCombinesWithCategory() throws Exception {
		Category roman = categoryRepository.save(new Category(null, "Roman", "roman"));
		Category bilim = categoryRepository.save(new Category(null, "Bilim", "bilim"));
		save(withCategories(book("Watchmen", "10.00"), roman));
		save(withCategories(book("Moby-Dick", "20.00"), roman));
		save(withCategories(book("Başka Kitap", "30.00"), bilim));

		assertTitles("q=watch", "Watchmen");
		assertTitles("q=WATCHMEN", "Watchmen");
		assertTitles("q=Moby", "Moby-Dick");
		assertTitles("q=watch&categoryId=" + roman.getId(), "Watchmen");
		assertTitles("q=Moby&categoryId=" + bilim.getId());
		mockMvc.perform(get("/api/books?q=&sort=price_asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3));
		mockMvc.perform(get("/api/books?q=   &sort=price_asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void categoryFilterIncludesSubcategoriesAndReturnsEachBookOnce() throws Exception {
		Category edebiyat = categoryRepository.save(new Category(null, "Edebiyat", "edebiyat"));
		Category roman = categoryRepository.save(new Category(edebiyat, "Roman", "roman"));
		Category oyku = categoryRepository.save(new Category(edebiyat, "Öykü", "oyku"));
		Category polisiye = categoryRepository.save(new Category(roman, "Polisiye", "polisiye"));
		Category bilim = categoryRepository.save(new Category(null, "Bilim", "bilim"));
		save(withCategories(book("Roman Kitabı", "10.00"), roman));
		save(withCategories(book("Öykü Kitabı", "20.00"), oyku));
		save(withCategories(book("İkisi Birden", "30.00"), roman, oyku));
		save(withCategories(book("Polisiye Kitabı", "40.00"), polisiye));
		save(withCategories(book("Genel Edebiyat", "50.00"), edebiyat));
		save(withCategories(book("Bilim Kitabı", "60.00"), bilim));

		mockMvc.perform(get("/api/books?sort=price_asc&categoryId=" + edebiyat.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title",
					contains("Roman Kitabı", "Öykü Kitabı", "İkisi Birden", "Polisiye Kitabı", "Genel Edebiyat")))
			.andExpect(jsonPath("$.totalElements").value(5));
		assertTitles("categoryId=" + roman.getId(), "Roman Kitabı", "İkisi Birden", "Polisiye Kitabı");
		assertTitles("categoryId=" + UUID.randomUUID());
	}

	@Test
	void allSortOrdersAreCorrectAndTiesAreOrderedById() throws Exception {
		Book older = save(withPublishedAt(book("Çay Saati", "100.00"), NOW.minus(3, ChronoUnit.DAYS)));
		Book newest = save(withPublishedAt(book("Ayna", "50.00"), NOW.minus(1, ChronoUnit.DAYS)));
		Book middle = save(withPublishedAt(book("Bulut", "100.00"), NOW.minus(2, ChronoUnit.DAYS)));
		Book oldest = save(withPublishedAt(book("Deniz", "100.00"), NOW.minus(4, ChronoUnit.DAYS)));
		Book cheap = save(withPublishedAt(book("Elma", "20.00"), NOW.minus(5, ChronoUnit.DAYS)));
		List<String> tiesById = byUnsignedId(older, middle, oldest);

		assertTitles("sort=newest", "Ayna", "Bulut", "Çay Saati", "Deniz", "Elma");
		assertTitles("sort=NEWEST", "Ayna", "Bulut", "Çay Saati", "Deniz", "Elma");
		assertTitles("sort=title_asc", "Ayna", "Bulut", "Çay Saati", "Deniz", "Elma");

		List<String> priceAsc = new ArrayList<>(List.of(cheap.getTitle(), newest.getTitle()));
		priceAsc.addAll(tiesById);
		assertTitles("sort=price_asc", priceAsc.toArray(String[]::new));

		List<String> priceDesc = new ArrayList<>(tiesById);
		priceDesc.addAll(List.of(newest.getTitle(), cheap.getTitle()));
		assertTitles("sort=price_desc", priceDesc.toArray(String[]::new));
	}

	@Test
	void paginationReportsTotalsAndEmptyPageAfterLast() throws Exception {
		for (int i = 1; i <= 5; i++) {
			save(book("Kitap " + i, i + "0.00"));
		}

		mockMvc.perform(get("/api/books?sort=price_asc&size=2&page=0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", contains("Kitap 1", "Kitap 2")))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalElements").value(5))
			.andExpect(jsonPath("$.totalPages").value(3));
		mockMvc.perform(get("/api/books?sort=price_asc&size=2&page=2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", contains("Kitap 5")))
			.andExpect(jsonPath("$.totalElements").value(5));
		mockMvc.perform(get("/api/books?sort=price_asc&size=2&page=3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(0)))
			.andExpect(jsonPath("$.page").value(3))
			.andExpect(jsonPath("$.totalElements").value(5))
			.andExpect(jsonPath("$.totalPages").value(3));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"size=51            | size",
		"size=0             | size",
		"page=-1            | page",
		"sort=foo           | sort",
		"minPrice=-1        | minPrice",
		"minPrice=10&maxPrice=5 | minPrice",
		"categoryId=bozuk   | categoryId",
		"page=abc           | page",
		"maxPrice=on-lira   | maxPrice",
		"q=a                | q",
		"q=x                | q" })
	void invalidQueryParametersReturn400ValidationFailed(String query, String field) throws Exception {
		String body = mockMvc.perform(get("/api/books?" + query))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", contains(field)))
			.andExpect(jsonPath("$.errors[0].message").isNotEmpty())
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("Exception").doesNotContain("bozuk").doesNotContain("on-lira")
			.doesNotContain("foo");
	}

	@Test
	void listQueryCountDoesNotDependOnAuthorCount() throws Exception {
		for (int i = 0; i < 20; i++) {
			Author first = authorRepository.save(new Author("Yazar " + i + "a", "yazar-" + i + "a"));
			Author other = authorRepository.save(new Author("Yazar " + i + "b", "yazar-" + i + "b"));
			save(withAuthors(book("Kitap " + i, "10.00"), first, other));
		}
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		mockMvc.perform(get("/api/books?size=20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(20)))
			.andExpect(jsonPath("$.items[*].authors[1].name").isNotEmpty())
			.andExpect(jsonPath("$.totalElements").value(20));

		assertThat(statistics.getPrepareStatementCount()).as("SQL statements for one list request").isLessThanOrEqualTo(4);
		assertThat(statistics.getCollectionFetchCount()).as("author collections loaded in one batch").isEqualTo(1);
	}

	@Test
	void detailOfPublishedBookContainsRelationsButNoInternalFields() throws Exception {
		Author zeynep = authorRepository.save(new Author("Zeynep", "zeynep"));
		Author ali = authorRepository.save(new Author("Ali", "ali"));
		Category roman = categoryRepository.save(new Category(null, "Roman", "roman"));
		Category oyku = categoryRepository.save(new Category(null, "Öykü", "oyku"));
		Book book = book("Detaylı Kitap", "42.50");
		book.setIsbn("9786050009999");
		book.setDescription("Açıklama");
		book.setPageCount(123);
		book = save(withCategories(withAuthors(book, zeynep, ali), roman, oyku));

		mockMvc.perform(get("/api/books/" + book.getId()))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(book.getId().toString()))
			.andExpect(jsonPath("$.title").value("Detaylı Kitap"))
			.andExpect(jsonPath("$.priceAmount").value(42.5))
			.andExpect(jsonPath("$.currency").value("TRY"))
			.andExpect(jsonPath("$.isbn").value("9786050009999"))
			.andExpect(jsonPath("$.pageCount").value(123))
			.andExpect(jsonPath("$.publishedAt").isNotEmpty())
			.andExpect(jsonPath("$.authors[*].name", contains("Ali", "Zeynep")))
			.andExpect(jsonPath("$.categories[*].name", contains("Öykü", "Roman")))
			.andExpect(jsonPath("$.stockQuantity").doesNotExist())
			.andExpect(jsonPath("$.reservedQuantity").doesNotExist())
			.andExpect(jsonPath("$.version").doesNotExist())
			.andExpect(jsonPath("$.status").doesNotExist())
			.andExpect(jsonPath("$.createdAt").doesNotExist());
	}

	@Test
	void detailOfDraftArchivedOrMissingBookIs404() throws Exception {
		Book draft = save(withStatus(book("Taslak", "10.00"), BookStatus.DRAFT));
		Book archived = save(withStatus(book("Arşiv", "10.00"), BookStatus.ARCHIVED));

		for (UUID id : List.of(draft.getId(), archived.getId(), UUID.randomUUID())) {
			mockMvc.perform(get("/api/books/" + id))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("Book not found."));
		}
	}

	@Test
	void detailWithMalformedIdIs400() throws Exception {
		mockMvc.perform(get("/api/books/bozuk-id"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void inStockReflectsSellableQuantity() throws Exception {
		save(book("Stokta", "10.00", 5));
		setReserved(save(book("Tamamı Rezerve", "20.00", 3)), 3);
		save(book("Stoksuz", "30.00", 0));

		mockMvc.perform(get("/api/books?sort=price_asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", contains("Stokta", "Tamamı Rezerve", "Stoksuz")))
			.andExpect(jsonPath("$.items[*].inStock", contains(true, false, false)))
			.andExpect(jsonPath("$.items[0].stockQuantity").doesNotExist());
	}

	@Test
	void listIsPublicAndIgnoresExpiredToken() throws Exception {
		save(book("Herkese Açık", "10.00"));

		mockMvc.perform(get("/api/books"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", contains("Herkese Açık")));
		mockMvc.perform(get("/api/books").with(bearer(TestJwt.expiredAdmin(SUBJECT))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", contains("Herkese Açık")));
	}

	private void assertTitles(String query, String... titles) throws Exception {
		String sortedQuery = query.contains("sort=") ? query : query + "&sort=price_asc";
		Matcher<?> expectedTitles = (titles.length == 0) ? hasSize(0) : contains(titles);
		mockMvc.perform(get("/api/books?" + sortedQuery))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", expectedTitles))
			.andExpect(jsonPath("$.totalElements").value(titles.length));
	}

	/** DB'de BINARY(16) bayt sırası = işaretsiz karşılaştırma; UUID.compareTo işaretli olduğu için kullanılmaz. */
	private static List<String> byUnsignedId(Book... books) {
		return List.of(books).stream()
			.sorted(Comparator.comparing((Book book) -> book.getId().getMostSignificantBits(), Long::compareUnsigned)
				.thenComparing(book -> book.getId().getLeastSignificantBits(), Long::compareUnsigned))
			.map(Book::getTitle)
			.toList();
	}

	private Book book(String title, String price) {
		return book(title, price, 1);
	}

	private Book book(String title, String price, int stock) {
		Book book = new Book(title, new BigDecimal(price), stock);
		book.setStatus(BookStatus.PUBLISHED);
		book.setPublishedAt(NOW);
		return book;
	}

	private Book save(Book book) {
		return bookRepository.save(book);
	}

	/** Rezerv entity üzerinden yazılamaz (updatable=false); kayıttan sonra doğrudan SQL ile verilir. */
	private void setReserved(Book book, int reserved) {
		jdbc.update("UPDATE books SET reserved_quantity = ? WHERE id = UUID_TO_BIN(?)", reserved,
				book.getId().toString());
	}

	private static Book withStatus(Book book, BookStatus status) {
		book.setStatus(status);
		return book;
	}

	private static Book withPublishedAt(Book book, Instant publishedAt) {
		book.setPublishedAt(publishedAt);
		return book;
	}

	private static Book withAuthors(Book book, Author... authors) {
		book.getAuthors().addAll(List.of(authors));
		return book;
	}

	private static Book withCategories(Book book, Category... categories) {
		book.getCategories().addAll(List.of(categories));
		return book;
	}

}
