package com.kitapsepeti.catalog.service;

import static com.kitapsepeti.catalog.repository.BookSpecifications.hasAuthor;
import static com.kitapsepeti.catalog.repository.BookSpecifications.hasPublisher;
import static com.kitapsepeti.catalog.repository.BookSpecifications.inAnyCategory;
import static com.kitapsepeti.catalog.repository.BookSpecifications.isPublished;
import static com.kitapsepeti.catalog.repository.BookSpecifications.priceAtLeast;
import static com.kitapsepeti.catalog.repository.BookSpecifications.priceAtMost;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kitapsepeti.catalog.dto.request.BookSearchRequest;
import com.kitapsepeti.catalog.dto.request.BookSort;
import com.kitapsepeti.catalog.dto.response.BookDetailResponse;
import com.kitapsepeti.catalog.dto.response.BookLookupResponse;
import com.kitapsepeti.catalog.dto.response.BookSummaryResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.BookStatus;
import com.kitapsepeti.catalog.mapper.BookMapper;
import com.kitapsepeti.catalog.repository.BookRepository;
import com.kitapsepeti.common.error.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Herkese açık kitap okuma. Yalnızca PUBLISHED kitaplar görünür; taslak ve arşiv dışarıdan ayırt edilemez (404).
 * Liste sorgusu: sayfa (yayınevi to-one JOIN FETCH) + sayım + yazarlar için tek toplu sorgu
 * ({@code default_batch_fetch_size}). To-many JOIN FETCH sayfalamayla kullanılmaz (Hibernate bellekte sayfalar).
 */
@Service
@Transactional(readOnly = true)
public class BookQueryService {

	private final BookRepository bookRepository;

	private final CategoryQueryService categoryQueryService;

	public BookQueryService(BookRepository bookRepository, CategoryQueryService categoryQueryService) {
		this.bookRepository = bookRepository;
		this.categoryQueryService = categoryQueryService;
	}

	public PageResponse<BookSummaryResponse> search(BookSearchRequest request) {
		PageRequest pageRequest = PageRequest.of(request.page(), request.size(), sortOf(request.sort()));
		return PageResponse.of(this.bookRepository.findAll(specificationOf(request), pageRequest),
				BookMapper::toSummary);
	}

	/**
	 * Verilen id'lerden yayındakiler, istekteki ilk geçiş sırasıyla; tekrarlı id bir kez, bulunamayan/taslak/arşiv
	 * atlanır. Kitap sayısından bağımsız iki sorgu: kitaplar + yayınevi, yazarlar (toplu).
	 */
	public BookLookupResponse lookup(List<UUID> ids) {
		List<UUID> distinctIds = List.copyOf(new LinkedHashSet<>(ids));
		Map<UUID, Book> published = this.bookRepository.findByIdInAndStatus(distinctIds, BookStatus.PUBLISHED)
			.stream()
			.collect(Collectors.toMap(Book::getId, Function.identity()));
		return new BookLookupResponse(distinctIds.stream()
			.map(published::get)
			.filter(Objects::nonNull)
			.map(BookMapper::toSummary)
			.toList());
	}

	/** @throws ResourceNotFoundException kitap yoksa veya yayında değilse */
	public BookDetailResponse getPublished(UUID id) {
		return this.bookRepository.findWithDetailsById(id)
			.filter(book -> book.getStatus() == BookStatus.PUBLISHED)
			.map(BookMapper::toDetail)
			.orElseThrow(() -> new ResourceNotFoundException("Book not found."));
	}

	private Specification<Book> specificationOf(BookSearchRequest request) {
		List<Specification<Book>> specifications = new ArrayList<>();
		specifications.add(isPublished());
		if (request.publisherId() != null) {
			specifications.add(hasPublisher(request.publisherId()));
		}
		if (request.authorId() != null) {
			specifications.add(hasAuthor(request.authorId()));
		}
		if (request.categoryId() != null) {
			specifications.add(inAnyCategory(this.categoryQueryService.subtreeIds(request.categoryId())));
		}
		if (request.minPrice() != null) {
			specifications.add(priceAtLeast(request.minPrice()));
		}
		if (request.maxPrice() != null) {
			specifications.add(priceAtMost(request.maxPrice()));
		}
		return Specification.allOf(specifications);
	}

	/** İkincil {@code id} sıralaması aynı değerli satırlarda sayfalar arası kayma/tekrarı önler. */
	private static Sort sortOf(BookSort sort) {
		return switch (sort) {
			case NEWEST -> Sort.by(Order.desc("publishedAt"), Order.desc("id"));
			case PRICE_ASC -> Sort.by(Order.asc("priceAmount"), Order.asc("id"));
			case PRICE_DESC -> Sort.by(Order.desc("priceAmount"), Order.asc("id"));
			case TITLE_ASC -> Sort.by(Order.asc("title"), Order.asc("id"));
		};
	}

}
