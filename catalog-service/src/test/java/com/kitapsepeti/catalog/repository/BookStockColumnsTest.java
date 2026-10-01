package com.kitapsepeti.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.Publisher;
import com.kitapsepeti.catalog.support.SqlCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stok/rezerv kolonları entity üzerinden yazılmaz ({@code updatable = false}): Hibernate'in UPDATE'i bu
 * kolonları içermez, bu yüzden bir admin düzenlemesi araya giren rezerv değişikliğini eski değerle ezemez.
 * Test metotları transaction'sız koşar (ApiTestSupport); transaction'lar burada açıkça yönetilir.
 */
class BookStockColumnsTest extends ApiTestSupport {

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private PublisherRepository publisherRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate tx;

	private TransactionTemplate requiresNew;

	private UUID bookId;

	@BeforeEach
	void setUp() {
		tx = new TransactionTemplate(transactionManager);
		requiresNew = new TransactionTemplate(transactionManager);
		requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		Publisher publisher = publisherRepository.save(new Publisher("Yayınevi", "yayinevi"));
		bookId = bookRepository.save(new Book("Eski Başlık", publisher, new BigDecimal("50.00"), 10)).getId();
	}

	@Test
	void entityUpdateDoesNotOverwriteReservedChangedByAnotherTransaction() {
		SqlCapture.start();
		tx.executeWithoutResult(outer -> {
			Book book = bookRepository.findById(bookId).orElseThrow();
			assertThat(book.getReservedQuantity()).isZero();
			book.setTitle("Yeni Başlık");

			// Commit'ten önce başka bir transaction rezervi değiştirip commit eder (ör. sipariş rezervasyonu).
			requiresNew.executeWithoutResult(inner -> jdbc.update(
					"UPDATE books SET reserved_quantity = 4 WHERE id = UUID_TO_BIN(?)", bookId.toString()));
		});
		List<String> statements = SqlCapture.stop();

		assertThat(column("reserved_quantity")).isEqualTo("4");
		assertThat(column("stock_quantity")).isEqualTo("10");
		assertThat(column("title")).isEqualTo("Yeni Başlık");
		assertThat(column("version")).isEqualTo("1");

		List<String> bookUpdates = statements.stream()
			.filter(sql -> sql.toLowerCase(Locale.ROOT).startsWith("update books"))
			.toList();
		assertThat(bookUpdates).hasSize(1);
		assertThat(bookUpdates.get(0)).contains("title")
			.contains("version")
			.doesNotContain("stock_quantity")
			.doesNotContain("reserved_quantity");
	}

	@Test
	void adjustStockIsSingleConditionalUpdateThatDoesNotTouchVersion() {
		SqlCapture.start();
		Integer increased = tx.execute(status -> bookRepository.adjustStock(bookId, 5));
		List<String> statements = SqlCapture.stop();
		jdbc.update("UPDATE books SET reserved_quantity = 12 WHERE id = UUID_TO_BIN(?)", bookId.toString());
		Integer belowReserved = tx.execute(status -> bookRepository.adjustStock(bookId, -4));

		assertThat(increased).isEqualTo(1);
		assertThat(belowReserved).isZero();
		assertThat(column("stock_quantity")).isEqualTo("15");
		assertThat(column("version")).isEqualTo("0");
		assertThat(statements).hasSize(1);
		assertThat(statements.get(0)).startsWith("update books")
			.contains("stock_quantity")
			.contains("reserved_quantity")
			.doesNotContain("version");
	}

	/**
	 * JPQL bulk UPDATE {@code @UpdateTimestamp}'i atlar, ama kolondaki {@code ON UPDATE CURRENT_TIMESTAMP(6)}
	 * değer değiştiren her UPDATE'te çalışır: başarılı ayarlama updated_at'i DB saatine çeker; koşulu
	 * tutmayan (0 satır) ayarlama dokunmaz.
	 */
	@Test
	void adjustStockRefreshesUpdatedAtThroughDatabaseDefault() {
		String past = "2020-01-01 00:00:00.000000";
		jdbc.update("UPDATE books SET updated_at = ? WHERE id = UUID_TO_BIN(?)", past, bookId.toString());

		Integer increased = tx.execute(status -> bookRepository.adjustStock(bookId, 5));

		assertThat(increased).isEqualTo(1);
		assertThat(column("CAST(updated_at AS CHAR)")).isNotEqualTo(past);
		assertThat(column("updated_at > NOW(6) - INTERVAL 1 MINUTE")).isEqualTo("1");
		assertThat(column("version")).isEqualTo("0");

		jdbc.update("UPDATE books SET updated_at = ?, reserved_quantity = 15 WHERE id = UUID_TO_BIN(?)", past,
				bookId.toString());
		Integer belowReserved = tx.execute(status -> bookRepository.adjustStock(bookId, -1));

		assertThat(belowReserved).isZero();
		assertThat(column("CAST(updated_at AS CHAR)")).isEqualTo(past);
	}

	private String column(String name) {
		return jdbc.queryForObject("SELECT " + name + " FROM books WHERE id = UUID_TO_BIN(?)", String.class,
				bookId.toString());
	}

}
