package com.kitapsepeti.catalog.mapper;

import com.kitapsepeti.catalog.dto.response.AdminBookResponse;
import com.kitapsepeti.catalog.dto.response.AdminBookSummaryResponse;
import com.kitapsepeti.catalog.entity.Book;

/** {@link Book} → admin yanıtları. Lazy ilişkilere dokunduğu için transaction içinde çağrılmalı. */
public final class AdminBookMapper {

	private AdminBookMapper() {
	}

	public static AdminBookSummaryResponse toSummary(Book book) {
		return new AdminBookSummaryResponse(book.getId(), book.getTitle(), book.getStatus().value(),
				book.getPriceAmount(), book.getCurrency(), book.getStockQuantity(), book.getReservedQuantity(),
				book.getAvailableQuantity(), book.getUpdatedAt(), book.getVersion());
	}

	public static AdminBookResponse toResponse(Book book) {
		return new AdminBookResponse(book.getId(), book.getTitle(), book.getIsbn(), book.getDescription(),
				book.getPageCount(), book.getCoverUrl(), book.getPriceAmount(), book.getCurrency(),
				book.getStockQuantity(), book.getReservedQuantity(), book.getAvailableQuantity(),
				book.getStatus().value(), book.getPublishedAt(), book.getVersion(), book.getCreatedAt(),
				book.getUpdatedAt(), BookMapper.authorRefs(book.getAuthors()),
				BookMapper.categoryRefs(book.getCategories()));
	}

}
