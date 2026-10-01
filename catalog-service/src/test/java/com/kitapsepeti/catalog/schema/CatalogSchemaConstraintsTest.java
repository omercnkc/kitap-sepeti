package com.kitapsepeti.catalog.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.catalog.TestcontainersConfiguration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V1 şemasındaki kısıtların DB seviyesinde uygulandığını doğrular (entity yok, düz SQL).
 * Her test kendi transaction'ında koşar ve geri alınır.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CatalogSchemaConstraintsTest {

	private static final int MYSQL_CHECK_CONSTRAINT_VIOLATED = 3819;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void flywayCreatesAllCatalogTables() {
		Integer applied = jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class);
		List<String> tables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
				""", String.class);

		assertThat(applied).isEqualTo(1);
		assertThat(tables).containsExactlyInAnyOrder("publishers", "authors", "categories", "books", "book_authors",
				"book_categories", "stock_reservations", "outbox");
	}

	@Test
	void rejectsReservedQuantityAboveStock() {
		byte[] bookId = insertBook(insertPublisher("p1"), null, 5);

		assertCheckViolation(() -> jdbc.update("UPDATE books SET reserved_quantity = 6 WHERE id = ?", (Object) bookId),
				"ck_books_reserved_le_stock");
	}

	@Test
	void rejectsNegativeStock() {
		byte[] publisherId = insertPublisher("p1");
		String clause = jdbc.queryForObject("""
				SELECT check_clause FROM information_schema.check_constraints
				WHERE constraint_schema = DATABASE() AND constraint_name = 'ck_books_stock_non_negative'
				""", String.class);

		assertThat(clause).isEqualTo("(`stock_quantity` >= 0)");
		// reserved_quantity >= 0 iken negatif stok ck_books_reserved_le_stock'u da ihlal eder; MySQL yalnızca birini raporlar.
		assertCheckViolation(() -> insertBook(publisherId, null, -1), "ck_books_stock_non_negative",
				"ck_books_reserved_le_stock");
	}

	@Test
	void rejectsUnknownBookStatus() {
		byte[] bookId = insertBook(insertPublisher("p1"), null, 0);

		assertCheckViolation(() -> jdbc.update("UPDATE books SET status = 'deleted' WHERE id = ?", (Object) bookId),
				"ck_books_status");
	}

	@Test
	void rejectsNegativePrice() {
		byte[] bookId = insertBook(insertPublisher("p1"), null, 0);

		assertCheckViolation(() -> jdbc.update("UPDATE books SET price_amount = -1 WHERE id = ?", (Object) bookId),
				"ck_books_price_non_negative");
	}

	@Test
	void rejectsDuplicatePublisherSlug() {
		insertPublisher("can-yayinlari");

		assertDuplicate(() -> insertPublisher("can-yayinlari"), "uk_publishers_slug");
	}

	@Test
	void allowsManyNullIsbnsButRejectsDuplicateIsbn() {
		byte[] publisherId = insertPublisher("p1");
		insertBook(publisherId, null, 0);
		insertBook(publisherId, null, 0);
		insertBook(publisherId, "9789750719387", 0);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM books WHERE isbn IS NULL", Integer.class)).isEqualTo(2);
		assertDuplicate(() -> insertBook(publisherId, "9789750719387", 0), "uk_books_isbn");
	}

	@Test
	void rejectsDeletingPublisherWithBooks() {
		byte[] publisherId = insertPublisher("p1");
		insertBook(publisherId, null, 0);

		assertViolation(() -> jdbc.update("DELETE FROM publishers WHERE id = ?", (Object) publisherId),
				"fk_books_publisher");
	}

	@Test
	void rejectsDeletingCategoryWithChildren() {
		byte[] parentId = insertCategory(null, "edebiyat");
		insertCategory(parentId, "roman");

		assertViolation(() -> jdbc.update("DELETE FROM categories WHERE id = ?", (Object) parentId),
				"fk_categories_parent");
	}

	@Test
	void deletingBookCascadesToLinksButKeepsAuthorAndCategory() {
		byte[] bookId = insertBook(insertPublisher("p1"), null, 0);
		byte[] authorId = insertAuthor("orhan-pamuk");
		byte[] categoryId = insertCategory(null, "roman");
		jdbc.update("INSERT INTO book_authors (book_id, author_id) VALUES (?, ?)", bookId, authorId);
		jdbc.update("INSERT INTO book_categories (book_id, category_id) VALUES (?, ?)", bookId, categoryId);

		jdbc.update("DELETE FROM books WHERE id = ?", (Object) bookId);

		assertThat(count("book_authors", "book_id", bookId)).isZero();
		assertThat(count("book_categories", "book_id", bookId)).isZero();
		assertThat(count("authors", "id", authorId)).isEqualTo(1);
		assertThat(count("categories", "id", categoryId)).isEqualTo(1);
	}

	@Test
	void rejectsDeletingAuthorWithBooks() {
		byte[] bookId = insertBook(insertPublisher("p1"), null, 0);
		byte[] authorId = insertAuthor("sabahattin-ali");
		jdbc.update("INSERT INTO book_authors (book_id, author_id) VALUES (?, ?)", bookId, authorId);

		assertViolation(() -> jdbc.update("DELETE FROM authors WHERE id = ?", (Object) authorId),
				"fk_book_authors_author");
	}

	@Test
	void allowsOneReservationPerOrderAndBook() {
		byte[] publisherId = insertPublisher("p1");
		byte[] firstBook = insertBook(publisherId, null, 10);
		byte[] secondBook = insertBook(publisherId, null, 10);
		byte[] orderId = newId();
		insertReservation(firstBook, orderId, 1);

		assertDuplicate(() -> insertReservation(firstBook, orderId, 2), "uk_stock_reservations_order_book");
		insertReservation(secondBook, orderId, 1);
		assertThat(count("stock_reservations", "order_id", orderId)).isEqualTo(2);
	}

	@Test
	void rejectsZeroQuantityReservation() {
		byte[] bookId = insertBook(insertPublisher("p1"), null, 10);

		assertCheckViolation(() -> insertReservation(bookId, newId(), 0), "ck_stock_reservations_quantity");
	}

	@Test
	void rejectsDeletingBookWithReservations() {
		byte[] bookId = insertBook(insertPublisher("p1"), null, 10);
		insertReservation(bookId, newId(), 1);

		assertViolation(() -> jdbc.update("DELETE FROM books WHERE id = ?", (Object) bookId),
				"fk_stock_reservations_book");
	}

	/**
	 * MySQL CHECK ihlalini SQLSTATE HY000 / hata 3819 ile bildirir; Spring bu durumu sınıflandıramaz ve
	 * JdbcTemplate {@link UncategorizedSQLException} fırlatır (DataIntegrityViolationException değil).
	 */
	private static void assertCheckViolation(ThrowingCallable statement, String... anyOfConstraints) {
		String[] messages = Arrays.stream(anyOfConstraints)
			.map(name -> "Check constraint '" + name + "' is violated")
			.toArray(String[]::new);
		assertThatThrownBy(statement)
			.isInstanceOfSatisfying(UncategorizedSQLException.class,
					ex -> assertThat(ex.getSQLException().getErrorCode()).isEqualTo(MYSQL_CHECK_CONSTRAINT_VIOLATED))
			.message()
			.containsAnyOf(messages);
	}

	private static void assertViolation(ThrowingCallable statement, String constraint) {
		assertThatThrownBy(statement).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining(constraint);
	}

	private static void assertDuplicate(ThrowingCallable statement, String constraint) {
		assertThatThrownBy(statement).isInstanceOf(DuplicateKeyException.class).hasMessageContaining(constraint);
	}

	private byte[] insertPublisher(String slug) {
		byte[] id = newId();
		jdbc.update("INSERT INTO publishers (id, name, slug) VALUES (?, ?, ?)", id, "Yayınevi " + slug, slug);
		return id;
	}

	private byte[] insertAuthor(String slug) {
		byte[] id = newId();
		jdbc.update("INSERT INTO authors (id, name, slug) VALUES (?, ?, ?)", id, "Yazar " + slug, slug);
		return id;
	}

	private byte[] insertCategory(byte[] parentId, String slug) {
		byte[] id = newId();
		jdbc.update("INSERT INTO categories (id, parent_id, name, slug) VALUES (?, ?, ?, ?)", id, parentId,
				"Kategori " + slug, slug);
		return id;
	}

	private byte[] insertBook(byte[] publisherId, String isbn, int stockQuantity) {
		byte[] id = newId();
		jdbc.update("""
				INSERT INTO books (id, isbn, title, publisher_id, price_amount, stock_quantity)
				VALUES (?, ?, ?, ?, ?, ?)
				""", id, isbn, "Kitap", publisherId, new BigDecimal("99.90"), stockQuantity);
		return id;
	}

	private void insertReservation(byte[] bookId, byte[] orderId, int quantity) {
		jdbc.update("""
				INSERT INTO stock_reservations (id, book_id, order_id, quantity, expires_at)
				VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6) + INTERVAL 15 MINUTE)
				""", newId(), bookId, orderId, quantity);
	}

	private int count(String table, String column, byte[] value) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class,
				(Object) value);
	}

	private static byte[] newId() {
		UUID uuid = UUID.randomUUID();
		return ByteBuffer.allocate(16)
			.putLong(uuid.getMostSignificantBits())
			.putLong(uuid.getLeastSignificantBits())
			.array();
	}

}
