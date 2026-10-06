package com.kitapsepeti.catalog.exception;

import java.math.BigDecimal;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.repository.AuthorRepository;
import com.kitapsepeti.catalog.repository.BookRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yalnızca testlerde: GlobalExceptionHandler'ın her dalını gerçek repository/DB hatalarıyla tetikler.
 * Hatalar repository proxy'sinden (Spring DataAccessException) çıktığı haliyle advice'a ulaşır.
 */
@RestController
@RequestMapping("/test/errors")
class ErrorProbeController {

	record AuthorRequest(String name, String slug) {
	}

	record BookRequest(String isbn) {
	}

	record ValidatedRequest(@NotBlank @Size(max = 10) String name, @NotBlank @Pattern(regexp = "[a-z0-9-]+") String slug) {
	}

	private final AuthorRepository authorRepository;

	private final BookRepository bookRepository;

	private final TransactionTemplate tx;

	private final TransactionTemplate requiresNew;

	ErrorProbeController(AuthorRepository authorRepository, BookRepository bookRepository,
			PlatformTransactionManager transactionManager) {
		this.authorRepository = authorRepository;
		this.bookRepository = bookRepository;
		this.tx = new TransactionTemplate(transactionManager);
		this.requiresNew = new TransactionTemplate(transactionManager);
		this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@PostMapping("/authors")
	UUID createAuthor(@RequestBody AuthorRequest request) {
		return authorRepository.saveAndFlush(new Author(request.name(), request.slug())).getId();
	}

	@PostMapping("/books")
	@Transactional
	UUID createBook(@RequestBody BookRequest request) {
		Book book = new Book("Kitap", new BigDecimal("10.00"));
		book.setIsbn(request.isbn());
		return bookRepository.saveAndFlush(book).getId();
	}

	@DeleteMapping("/authors/{id}")
	@Transactional
	void deleteAuthor(@PathVariable UUID id) {
		authorRepository.deleteById(id);
		authorRepository.flush();
	}

	@PostMapping("/books/overbooked")
	@Transactional
	UUID createOverbookedBook() {
		Book book = new Book("Kitap", new BigDecimal("10.00"), -1);
		return bookRepository.saveAndFlush(book).getId();
	}

	/** Okunan versiyon, araya giren (REQUIRES_NEW) başka bir güncellemeyle bayatlar; sonra bayat kopya yazılır. */
	@PostMapping("/books/{id}/stale-update")
	void staleUpdate(@PathVariable UUID id) {
		tx.executeWithoutResult(outer -> {
			Book stale = bookRepository.findById(id).orElseThrow();
			requiresNew.executeWithoutResult(inner -> bookRepository.findById(id).orElseThrow().setTitle("İlk"));
			stale.setTitle("Bayat");
			bookRepository.saveAndFlush(stale);
		});
	}

	@PostMapping("/validate")
	String validate(@Valid @RequestBody ValidatedRequest request) {
		return "ok";
	}

	@GetMapping("/unexpected")
	String unexpected() {
		throw new IllegalStateException("gizli-ic-detay-4411");
	}

}
