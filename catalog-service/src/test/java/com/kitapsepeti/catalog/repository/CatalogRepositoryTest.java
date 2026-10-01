package com.kitapsepeti.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.catalog.TestcontainersConfiguration;
import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.BookStatus;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.entity.Publisher;
import com.kitapsepeti.catalog.entity.ReservationStatus;
import com.kitapsepeti.catalog.entity.StockReservation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Entity eşlemeleri ve repository sorguları (gerçek MySQL, Flyway V1, ddl-auto validate).
 * Her test kendi transaction'ında koşar ve geri alınır.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CatalogRepositoryTest {

	@Autowired
	private PublisherRepository publisherRepository;

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private StockReservationRepository reservationRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void contextStartsWithSchemaValidation() {
		Object ddlAuto = entityManager.getEntityManagerFactory().getProperties().get("hibernate.hbm2ddl.auto");

		assertThat(ddlAuto).isEqualTo("validate");
		assertThat(entityManager.getMetamodel().getEntities()).extracting(EntityType::getName)
			.containsExactlyInAnyOrder("Publisher", "Author", "Category", "Book", "StockReservation", "OutboxEvent");
	}

	@Test
	void savesAndReadsPublisherAuthorAndCategoryWithUuidV7Ids() {
		UUID publisherId = publisherRepository.saveAndFlush(new Publisher("Can Yayınları", "can-yayinlari")).getId();
		UUID authorId = authorRepository.saveAndFlush(new Author("Orhan Pamuk", "orhan-pamuk")).getId();
		UUID categoryId = categoryRepository.saveAndFlush(new Category(null, "Roman", "roman")).getId();
		entityManager.clear();

		Publisher publisher = publisherRepository.findBySlug("can-yayinlari").orElseThrow();
		Author author = authorRepository.findBySlug("orhan-pamuk").orElseThrow();
		Category category = categoryRepository.findBySlug("roman").orElseThrow();

		assertThat(List.of(publisherId, authorId, categoryId)).allSatisfy(id -> assertThat(id.version()).isEqualTo(7));
		assertThat(publisher.getId()).isEqualTo(publisherId);
		assertThat(publisher.getName()).isEqualTo("Can Yayınları");
		assertThat(publisher.getCreatedAt()).isNotNull();
		assertThat(publisher.getUpdatedAt()).isNotNull();
		assertThat(author.getId()).isEqualTo(authorId);
		assertThat(author.getName()).isEqualTo("Orhan Pamuk");
		assertThat(category.getId()).isEqualTo(categoryId);
		assertThat(category.getParent()).isNull();
		assertThat(publisherRepository.existsBySlug("can-yayinlari")).isTrue();
		assertThat(authorRepository.existsBySlug("yok")).isFalse();
		assertThat(categoryRepository.existsBySlug("roman")).isTrue();
	}

	@Test
	void hashCodeOfSetMembersIsStableAcrossPersist() {
		Author author = new Author("Yaşar Kemal", "yasar-kemal");
		Category category = new Category(null, "Klasik", "klasik");
		Set<Author> authors = new HashSet<>(Set.of(author));
		Set<Category> categories = new HashSet<>(Set.of(category));
		int authorHash = author.hashCode();
		int categoryHash = category.hashCode();

		authorRepository.saveAndFlush(author);
		categoryRepository.saveAndFlush(category);

		assertThat(author.getId()).isNotNull();
		assertThat(category.getId()).isNotNull();
		assertThat(author.hashCode()).isEqualTo(authorHash);
		assertThat(category.hashCode()).isEqualTo(categoryHash);
		assertThat(authors).contains(author);
		assertThat(categories).contains(category);
	}

	@Test
	void savesChildCategoryWithParentAndListsOnlyRoots() {
		Category edebiyat = categoryRepository.save(new Category(null, "Edebiyat", "edebiyat"));
		Category bilim = categoryRepository.save(new Category(null, "Bilim", "bilim"));
		Category roman = categoryRepository.saveAndFlush(new Category(edebiyat, "Roman", "roman"));
		entityManager.clear();

		Category child = categoryRepository.findById(roman.getId()).orElseThrow();
		List<Category> roots = categoryRepository.findAllByParentIsNull();

		assertThat(child.getParent().getId()).isEqualTo(edebiyat.getId());
		assertThat(roots).extracting(Category::getId).containsExactlyInAnyOrder(edebiyat.getId(), bilim.getId());
	}

	@Test
	void savesBookWithTwoAuthorsAndTwoCategories() {
		Book book = newBook("Kitap");
		book.getAuthors().addAll(List.of(saveAuthor("a1"), saveAuthor("a2")));
		book.getCategories().addAll(List.of(saveCategory("c1"), saveCategory("c2")));

		bookRepository.saveAndFlush(book);

		assertThat(countLinks("book_authors", book.getId())).isEqualTo(2);
		assertThat(countLinks("book_categories", book.getId())).isEqualTo(2);
	}

	@Test
	void removingAuthorFromBookDeletesOnlyThatLink() {
		Author first = saveAuthor("a1");
		Author second = saveAuthor("a2");
		Book book = newBook("Kitap");
		book.getAuthors().addAll(List.of(first, second));
		bookRepository.saveAndFlush(book);

		book.getAuthors().remove(first);
		bookRepository.saveAndFlush(book);

		assertThat(countLinks("book_authors", book.getId())).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(author_id) FROM book_authors WHERE book_id = UUID_TO_BIN(?)",
				String.class, book.getId().toString())).isEqualTo(second.getId().toString());
		assertThat(authorRepository.existsById(first.getId())).isTrue();
	}

	@Test
	void storesBookStatusInLowerCase() {
		Book book = newBook("Kitap");
		book.setStatus(BookStatus.PUBLISHED);
		bookRepository.saveAndFlush(book);
		entityManager.clear();

		String stored = jdbc.queryForObject("SELECT status FROM books WHERE id = UUID_TO_BIN(?)", String.class,
				book.getId().toString());

		assertThat(stored).isEqualTo("published");
		assertThat(bookRepository.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(BookStatus.PUBLISHED);
	}

	@Test
	void keepsPriceScale() {
		Book book = new Book("Kitap", savePublisher("p1"), new BigDecimal("149.90"));
		bookRepository.saveAndFlush(book);
		entityManager.clear();

		Book found = bookRepository.findById(book.getId()).orElseThrow();

		assertThat(found.getPriceAmount()).isEqualByComparingTo("149.90");
		assertThat(found.getCurrency()).isEqualTo("TRY");
		assertThat(found.getStatus()).isEqualTo(BookStatus.DRAFT);
	}

	@Test
	void updateIncrementsVersionAndRefreshesUpdatedAt() {
		Book book = bookRepository.saveAndFlush(newBook("Kitap"));
		assertThat(book.getVersion()).isZero();

		book.setTitle("Yeni Başlık");
		bookRepository.saveAndFlush(book);

		assertThat(book.getVersion()).isEqualTo(1L);
		assertThat(book.getUpdatedAt()).isAfterOrEqualTo(book.getCreatedAt());
		assertThat(jdbc.queryForObject("SELECT version FROM books WHERE id = UUID_TO_BIN(?)", Long.class,
				book.getId().toString())).isEqualTo(1L);
	}

	@Test
	void savesReservationAndFindsItByOrderAndBook() {
		Book book = newBook("Kitap");
		book.setStockQuantity(10);
		bookRepository.save(book);
		UUID orderId = UUID.randomUUID();
		Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
		reservationRepository.saveAndFlush(new StockReservation(book, orderId, 2, expiresAt));
		entityManager.clear();

		StockReservation found = reservationRepository.findByOrderIdAndBookId(orderId, book.getId()).orElseThrow();
		String stored = jdbc.queryForObject("SELECT status FROM stock_reservations WHERE id = UUID_TO_BIN(?)",
				String.class, found.getId().toString());

		assertThat(found.getId().version()).isEqualTo(7);
		assertThat(found.getQuantity()).isEqualTo(2);
		assertThat(found.getStatus()).isEqualTo(ReservationStatus.HELD);
		assertThat(stored).isEqualTo("held");
		assertThat(reservationRepository.findAllByOrderId(orderId)).hasSize(1);
		assertThat(reservationRepository.findByOrderIdAndBookId(UUID.randomUUID(), book.getId())).isEmpty();
	}

	// --- Hata çevirisi (JPA save + flush). JdbcTemplate'ten farklı olarak Hibernate MySQL 3819'u (CHECK) da
	// ConstraintViolationException'a çevirir; üç ihlal de tam olarak DataIntegrityViolationException olur
	// (UNIQUE için DuplicateKeyException DEĞİL). Kısıt türü ve adı Hibernate istisnasında hazır gelir.

	@Test
	void checkViolationIsTranslatedToDataIntegrityViolation() {
		Book book = newBook("Kitap");
		book.setStockQuantity(1);
		book.setReservedQuantity(2);

		Throwable thrown = catchThrowable(() -> bookRepository.saveAndFlush(book));

		assertTranslated(thrown, ConstraintKind.CHECK, "ck_books_reserved_le_stock", SQLException.class);
	}

	@Test
	void uniqueViolationIsTranslatedToDataIntegrityViolation() {
		savePublisher("ayni-slug");

		Throwable thrown = catchThrowable(() -> publisherRepository.saveAndFlush(new Publisher("İkinci", "ayni-slug")));

		// MySQL 8 UNIQUE ihlalinde anahtarı tablo adıyla birlikte raporlar.
		assertTranslated(thrown, ConstraintKind.UNIQUE, "publishers.uk_publishers_slug",
				SQLIntegrityConstraintViolationException.class);
	}

	@Test
	void foreignKeyViolationIsTranslatedToDataIntegrityViolation() {
		Book book = bookRepository.saveAndFlush(newBook("Kitap"));
		UUID publisherId = book.getPublisher().getId();
		entityManager.clear();

		Throwable thrown = catchThrowable(() -> {
			publisherRepository.deleteById(publisherId);
			publisherRepository.flush();
		});

		assertTranslated(thrown, ConstraintKind.FOREIGN_KEY, "fk_books_publisher",
				SQLIntegrityConstraintViolationException.class);
	}

	private static void assertTranslated(Throwable thrown, ConstraintKind kind, String constraint,
			Class<? extends SQLException> rootCause) {
		assertThat(thrown).isExactlyInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining(constraint);
		assertThat(NestedExceptionUtils.getMostSpecificCause(thrown)).isExactlyInstanceOf(rootCause);
		assertThat(thrown.getCause()).isInstanceOfSatisfying(ConstraintViolationException.class, hibernate -> {
			assertThat(hibernate.getKind()).isEqualTo(kind);
			assertThat(hibernate.getConstraintName()).isEqualTo(constraint);
		});
	}

	private Publisher savePublisher(String slug) {
		return publisherRepository.save(new Publisher("Yayınevi " + slug, slug));
	}

	private Author saveAuthor(String slug) {
		return authorRepository.save(new Author("Yazar " + slug, slug));
	}

	private Category saveCategory(String slug) {
		return categoryRepository.save(new Category(null, "Kategori " + slug, slug));
	}

	private Book newBook(String title) {
		return new Book(title, savePublisher("p-" + UUID.randomUUID()), new BigDecimal("99.90"));
	}

	private int countLinks(String table, UUID bookId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE book_id = UUID_TO_BIN(?)", Integer.class,
				bookId.toString());
	}

}
