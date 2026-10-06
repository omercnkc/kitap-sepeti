package com.kitapsepeti.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.dto.request.BookLookupRequest;
import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.BookStatus;
import com.kitapsepeti.catalog.repository.AuthorRepository;
import com.kitapsepeti.catalog.repository.BookRepository;
import com.kitapsepeti.catalog.support.SqlCapture;
import com.kitapsepeti.catalog.support.TestJwt;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** {@code GET /api/books/lookup}: toplu okuma; her test kendi verisini kurar. */
class BookLookupControllerTest extends ApiTestSupport {

	private static final String LOOKUP = "/api/books/lookup";

	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private Author author;

	@BeforeEach
	void createDefaults() {
		author = authorRepository.save(new Author("Yazar", "yazar"));
	}

	@Test
	void returnsOnlyPublishedBooksInRequestOrder() throws Exception {
		Book first = save(book("Birinci", "10.00", 1));
		Book second = save(book("İkinci", "20.00", 1));
		Book third = save(book("Üçüncü", "30.00", 1));
		Book draft = save(withStatus(book("Taslak", "10.00", 1), BookStatus.DRAFT));
		Book archived = save(withStatus(book("Arşiv", "10.00", 1), BookStatus.ARCHIVED));

		mockMvc.perform(get(LOOKUP).param("ids", ids(third.getId(), draft.getId(), first.getId(), UUID.randomUUID(),
				archived.getId(), second.getId())))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.items[*].title", contains("Üçüncü", "Birinci", "İkinci")))
			.andExpect(jsonPath("$.items[0].id").value(third.getId().toString()))
			.andExpect(jsonPath("$.items[0].priceAmount").value(30.0))
			.andExpect(jsonPath("$.items[0].currency").value("TRY"))
			.andExpect(jsonPath("$.items[0].authors[*].name", contains("Yazar")));
	}

	@Test
	void duplicateIdsAreReturnedOnceAtFirstPosition() throws Exception {
		Book first = save(book("Birinci", "10.00", 1));
		Book second = save(book("İkinci", "20.00", 1));

		mockMvc.perform(get(LOOKUP).param("ids", ids(second.getId(), first.getId(), second.getId(), first.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", contains("İkinci", "Birinci")));
	}

	@Test
	void commaSeparatedAndRepeatedParametersAreEquivalent() throws Exception {
		Book first = save(book("Birinci", "10.00", 1));
		Book second = save(book("İkinci", "20.00", 1));

		mockMvc.perform(get(LOOKUP + "?ids=" + second.getId() + "," + first.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", contains("İkinci", "Birinci")));
		mockMvc.perform(get(LOOKUP + "?ids=" + second.getId() + "&ids=" + first.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", contains("İkinci", "Birinci")));
	}

	@Test
	void noMatchReturnsEmptyItems() throws Exception {
		mockMvc.perform(get(LOOKUP).param("ids", UUID.randomUUID().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void inStockReflectsSellableQuantityWithoutExposingCounts() throws Exception {
		Book inStock = save(book("Stokta", "10.00", 5));
		Book allReserved = save(book("Tamamı Rezerve", "20.00", 3));
		setReserved(allReserved, 3);
		Book noStock = save(book("Stoksuz", "30.00", 0));

		mockMvc.perform(get(LOOKUP).param("ids", ids(inStock.getId(), allReserved.getId(), noStock.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].inStock", contains(true, false, false)))
			.andExpect(jsonPath("$.items[0].stockQuantity").doesNotExist())
			.andExpect(jsonPath("$.items[0].reservedQuantity").doesNotExist())
			.andExpect(jsonPath("$.items[0].status").doesNotExist());
	}

	@Test
	void fiftyIdsAreAcceptedFiftyOneAreRejectedWithoutEchoingIds() throws Exception {
		Book book = save(book("Birinci", "10.00", 1));
		List<UUID> fifty = new ArrayList<>(List.of(book.getId()));
		fifty.addAll(randomIds(BookLookupRequest.MAX_IDS - 1));

		mockMvc.perform(get(LOOKUP).param("ids", ids(fifty.toArray(UUID[]::new))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", contains("Birinci")));

		List<UUID> fiftyOne = new ArrayList<>(fifty);
		fiftyOne.add(UUID.randomUUID());
		String body = assertValidationFailed(LOOKUP + "?ids=" + ids(fiftyOne.toArray(UUID[]::new)));
		assertThat(body).contains(String.valueOf(BookLookupRequest.MAX_IDS));
		for (UUID id : fiftyOne) {
			assertThat(body).doesNotContain(id.toString());
		}
	}

	@Test
	void duplicatesCountTowardsTheLimit() throws Exception {
		UUID id = UUID.randomUUID();
		String ids = Stream.generate(id::toString).limit(BookLookupRequest.MAX_IDS + 1).collect(Collectors.joining(","));

		String body = assertValidationFailed(LOOKUP + "?ids=" + ids);
		assertThat(body).doesNotContain(id.toString());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "?ids=", "?ids=bozuk-id-7712", "?ids=bozuk-id-7712,00000000-0000-0000-0000-000000000001",
			"?ids=00000000-0000-0000-0000-000000000001,,00000000-0000-0000-0000-000000000002" })
	void missingEmptyOrMalformedIdsReturn400ValidationFailed(String query) throws Exception {
		String body = assertValidationFailed(LOOKUP + query);

		assertThat(body).doesNotContain("bozuk-id-7712").doesNotContain("00000000-0000").doesNotContain("Exception");
	}

	@Test
	void sqlCountIsTheSameForOneAndFiftyBooks() throws Exception {
		List<UUID> fifty = new ArrayList<>();
		for (int i = 0; i < BookLookupRequest.MAX_IDS; i++) {
			Author own = authorRepository.save(new Author("Yazar " + i, "yazar-" + i));
			fifty.add(save(withAuthors(book("Kitap " + i, "10.00", 1), own)).getId());
		}
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

		statistics.clear();
		mockMvc.perform(get(LOOKUP).param("ids", fifty.get(0).toString()))
			.andExpect(jsonPath("$.items", hasSize(1)));
		long oneBook = statistics.getPrepareStatementCount();

		statistics.clear();
		SqlCapture.start();
		mockMvc.perform(get(LOOKUP).param("ids", ids(fifty.toArray(UUID[]::new))))
			.andExpect(jsonPath("$.items", hasSize(BookLookupRequest.MAX_IDS)))
			.andExpect(jsonPath("$.items[49].authors[0].name").value("Yazar 49"));
		List<String> statements = SqlCapture.stop();
		long fiftyBooks = statistics.getPrepareStatementCount();
		assertThat(oneBook).as("SQL statements for 1 id").isEqualTo(2);
		assertThat(fiftyBooks).as("SQL statements for 50 ids").isEqualTo(oneBook);
		assertThat(statistics.getCollectionFetchCount()).as("author collections loaded in one batch").isEqualTo(1);
		assertThat(statements).hasSize(2);
		assertThat(statements.get(0)).contains("from books").contains(" in (").contains("status=?");
		assertThat(statements.get(1)).contains("from book_authors").contains("book_id in (");
	}

	@Test
	void isPublicAndIgnoresBrokenOrExpiredAuthorization() throws Exception {
		Book book = save(book("Herkese Açık", "10.00", 1));

		mockMvc.perform(get(LOOKUP).param("ids", book.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", contains("Herkese Açık")));
		mockMvc.perform(get(LOOKUP).param("ids", book.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer bu-bir-jwt-degil"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", contains("Herkese Açık")));
		mockMvc.perform(get(LOOKUP).param("ids", book.getId().toString()).with(bearer(TestJwt.expiredAdmin(SUBJECT))))
			.andExpect(status().isOk());
	}

	private String assertValidationFailed(String url) throws Exception {
		return mockMvc.perform(get(url))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.instance").value(LOOKUP))
			.andExpect(jsonPath("$.errors[0].field").value(org.hamcrest.Matchers.startsWith("ids")))
			.andExpect(jsonPath("$.errors[0].message").isNotEmpty())
			.andReturn().getResponse().getContentAsString();
	}

	private static String ids(UUID... ids) {
		return Stream.of(ids).map(UUID::toString).collect(Collectors.joining(","));
	}

	private static List<UUID> randomIds(int count) {
		return Stream.generate(UUID::randomUUID).limit(count).toList();
	}

	private Book book(String title, String price, int stock) {
		Book book = new Book(title, new BigDecimal(price), stock);
		book.setStatus(BookStatus.PUBLISHED);
		book.setPublishedAt(NOW);
		book.getAuthors().add(author);
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

	private static Book withAuthors(Book book, Author... authors) {
		book.getAuthors().clear();
		book.getAuthors().addAll(List.of(authors));
		return book;
	}

}
