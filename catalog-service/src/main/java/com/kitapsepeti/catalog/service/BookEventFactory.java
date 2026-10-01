package com.kitapsepeti.catalog.service;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.mapper.BookMapper;
import com.kitapsepeti.catalog.mapper.NameOrder;
import com.kitapsepeti.catalog.repository.CategoryRepository;
import com.kitapsepeti.catalog.service.event.BookRemovedEvent;
import com.kitapsepeti.catalog.service.event.BookUpsertedEvent;
import org.springframework.stereotype.Component;

/**
 * Kitap olaylarının payload'larını kurar. Transaction içinde çağrılmalı (lazy ilişkilere dokunur).
 * {@code categoryIdsWithAncestors} tüm kategori ağacı tek sorguda okunarak hesaplanır (tablo küçük).
 */
@Component
public class BookEventFactory {

	/** Outbox {@code aggregate_type}; user-service'teki {@code "user"} gibi küçük harf. */
	public static final String BOOK_AGGREGATE = "book";

	private final CategoryRepository categoryRepository;

	public BookEventFactory(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	public BookUpsertedEvent upserted(Book book) {
		List<UUID> bookCategoryIds = book.getCategories().stream().map(Category::getId).toList();
		List<UUID> withAncestors = List.copyOf(
				CategoryForest.of(this.categoryRepository.findAll()).withAncestors(bookCategoryIds));
		return new BookUpsertedEvent(BookUpsertedEvent.VERSION, book.getId(), book.getTitle(), book.getIsbn(),
				book.getDescription(), book.getPriceAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
				book.getCurrency(), book.getCoverUrl(), book.getPageCount(), BookMapper.inStock(book),
				book.getPublishedAt(),
				new BookUpsertedEvent.Ref(book.getPublisher().getId(), book.getPublisher().getName(),
						book.getPublisher().getSlug()),
				refs(book.getAuthors(), Author::getName, Author::getSlug, Author::getId),
				refs(book.getCategories(), Category::getName, Category::getSlug, Category::getId), withAncestors,
				Instant.now());
	}

	public BookRemovedEvent removed(Book book) {
		return new BookRemovedEvent(BookRemovedEvent.VERSION, book.getId(), Instant.now());
	}

	/** Public yanıtlarla aynı sıra: Türkçe ada göre, eşitlikte slug. */
	private static <T> List<BookUpsertedEvent.Ref> refs(Collection<T> items, Function<T, String> name,
			Function<T, String> slug, Function<T, UUID> id) {
		return items.stream()
			.sorted(Comparator.comparing(name, NameOrder.TURKISH).thenComparing(slug))
			.map(item -> new BookUpsertedEvent.Ref(id.apply(item), name.apply(item), slug.apply(item)))
			.toList();
	}

}
