package com.kitapsepeti.catalog.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.BookStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Kitap listesi filtreleri. Çoka çok ilişkiler (yazar, kategori) ana sorguya JOIN edilmez; EXISTS alt sorgusuyla
 * süzülür. Böylece birden çok eşleşen kategorideki kitap tekrarlanmaz, DISTINCT ve sayım sorgusu sade kalır.
 */
public final class BookSpecifications {

	private BookSpecifications() {
	}

	public static Specification<Book> isPublished() {
		return (root, query, cb) -> cb.equal(root.get("status"), BookStatus.PUBLISHED);
	}

	public static Specification<Book> hasAuthor(UUID authorId) {
		return (root, query, cb) -> {
			Subquery<Integer> subquery = query.subquery(Integer.class);
			Root<Book> book = subquery.correlate(root);
			Join<Book, ?> author = book.join("authors");
			return cb.exists(subquery.select(cb.literal(1)).where(cb.equal(author.get("id"), authorId)));
		};
	}

	/** Boş küme hiçbir kitapla eşleşmez (var olmayan kategori → boş sonuç). */
	public static Specification<Book> inAnyCategory(Collection<UUID> categoryIds) {
		return (root, query, cb) -> {
			if (categoryIds.isEmpty()) {
				return cb.disjunction();
			}
			Subquery<Integer> subquery = query.subquery(Integer.class);
			Root<Book> book = subquery.correlate(root);
			Join<Book, ?> category = book.join("categories");
			return cb.exists(subquery.select(cb.literal(1)).where(category.get("id").in(categoryIds)));
		};
	}

	public static Specification<Book> priceAtLeast(BigDecimal minPrice) {
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("priceAmount"), minPrice);
	}

	public static Specification<Book> priceAtMost(BigDecimal maxPrice) {
		return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("priceAmount"), maxPrice);
	}

}
