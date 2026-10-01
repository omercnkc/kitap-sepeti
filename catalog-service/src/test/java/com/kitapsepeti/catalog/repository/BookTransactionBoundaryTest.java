package com.kitapsepeti.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.util.UUID;

import com.kitapsepeti.catalog.TestcontainersConfiguration;
import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.entity.Publisher;
import org.hibernate.LazyInitializationException;
import org.hibernate.StaleStateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transaction sınırına bağlı davranışlar: test metodu transaction'sız koşar (aksi halde lazy alanlar her zaman
 * yüklenebilir ve iki transaction aynı kalırdı). Veri commit edildiği için her testten sonra temizlenir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookTransactionBoundaryTest {

	@Autowired
	private PublisherRepository publisherRepository;

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbc;

	private TransactionTemplate tx;

	private TransactionTemplate requiresNew;

	@BeforeEach
	void setUp() {
		tx = new TransactionTemplate(transactionManager);
		requiresNew = new TransactionTemplate(transactionManager);
		requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM book_authors");
		jdbc.update("DELETE FROM book_categories");
		jdbc.update("DELETE FROM stock_reservations");
		jdbc.update("DELETE FROM books");
		jdbc.update("DELETE FROM authors");
		jdbc.update("UPDATE categories SET parent_id = NULL");
		jdbc.update("DELETE FROM categories");
		jdbc.update("DELETE FROM publishers");
	}

	@Test
	void findWithDetailsByIdLoadsAssociationsForUseOutsideTransaction() {
		UUID bookId = createBookWithAuthorAndCategory();

		Book plain = bookRepository.findById(bookId).orElseThrow();
		Book detailed = bookRepository.findWithDetailsById(bookId).orElseThrow();

		// Kontrol: entity graph olmadan aynı erişim transaction dışında patlar; test anlamlı.
		assertThatThrownBy(() -> plain.getAuthors().size()).isInstanceOf(LazyInitializationException.class);
		assertThat(detailed.getPublisher().getName()).isEqualTo("Yayınevi");
		assertThat(detailed.getAuthors()).extracting(Author::getName).containsExactly("Yazar");
		assertThat(detailed.getCategories()).extracting(Category::getName).containsExactly("Roman");
	}

	@Test
	void staleVersionUpdateIsRejected() {
		UUID bookId = createBookWithAuthorAndCategory();

		Throwable thrown = catchThrowable(() -> tx.executeWithoutResult(outer -> {
			Book stale = bookRepository.findById(bookId).orElseThrow();
			assertThat(stale.getVersion()).isZero();

			requiresNew.executeWithoutResult(inner -> {
				Book fresh = bookRepository.findById(bookId).orElseThrow();
				fresh.setTitle("İlk güncelleme");
			});

			stale.setTitle("Bayat güncelleme");
			bookRepository.saveAndFlush(stale);
		}));

		assertThat(thrown).isInstanceOf(ObjectOptimisticLockingFailureException.class)
			.hasRootCauseInstanceOf(StaleStateException.class)
			.hasMessageContaining("where id=? and version=?");
		Book current = bookRepository.findById(bookId).orElseThrow();
		assertThat(current.getTitle()).isEqualTo("İlk güncelleme");
		assertThat(current.getVersion()).isEqualTo(1L);
	}

	private UUID createBookWithAuthorAndCategory() {
		return tx.execute(status -> {
			Publisher publisher = publisherRepository.save(new Publisher("Yayınevi", "yayinevi"));
			Book book = new Book("Kitap", publisher, new BigDecimal("50.00"));
			book.getAuthors().add(authorRepository.save(new Author("Yazar", "yazar")));
			book.getCategories().add(categoryRepository.save(new Category(null, "Roman", "roman")));
			return bookRepository.save(book).getId();
		});
	}

}
