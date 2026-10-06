package com.kitapsepeti.catalog.mapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.kitapsepeti.catalog.dto.response.AuthorRef;
import com.kitapsepeti.catalog.dto.response.BookDetailResponse;
import com.kitapsepeti.catalog.dto.response.BookSummaryResponse;
import com.kitapsepeti.catalog.dto.response.CategoryRef;
import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.Category;

/**
 * {@link Book} → yanıt DTO'ları. Lazy ilişkilere dokunduğu için transaction içinde çağrılmalı.
 * Stok miktarları, versiyon, durum ve zaman damgaları yanıta taşınmaz.
 */
public final class BookMapper {

	private BookMapper() {
	}

	public static BookSummaryResponse toSummary(Book book) {
		return new BookSummaryResponse(book.getId(), book.getTitle(), book.getCoverUrl(), book.getPriceAmount(),
				book.getCurrency(), inStock(book), authorRefs(book.getAuthors()));
	}

	public static BookDetailResponse toDetail(Book book) {
		return new BookDetailResponse(book.getId(), book.getTitle(), book.getCoverUrl(), book.getPriceAmount(),
				book.getCurrency(), inStock(book), authorRefs(book.getAuthors()), book.getIsbn(), book.getDescription(),
				book.getPageCount(), book.getPublishedAt(), categoryRefs(book.getCategories()));
	}

	/** Satılabilir stok = stok - rezerv. */
	public static boolean inStock(Book book) {
		return book.getAvailableQuantity() > 0;
	}

	static List<AuthorRef> authorRefs(Collection<Author> authors) {
		return authors.stream()
			.sorted(Comparator.comparing(Author::getName, NameOrder.TURKISH).thenComparing(Author::getSlug))
			.map(author -> new AuthorRef(author.getId(), author.getName(), author.getSlug()))
			.toList();
	}

	static List<CategoryRef> categoryRefs(Collection<Category> categories) {
		return categories.stream()
			.sorted(Comparator.comparing(Category::getName, NameOrder.TURKISH).thenComparing(Category::getSlug))
			.map(category -> new CategoryRef(category.getId(), category.getName(), category.getSlug()))
			.toList();
	}

}
