package com.kitapsepeti.catalog.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.catalog.config.S3Properties;
import com.kitapsepeti.catalog.dto.request.AdminBookListRequest;
import com.kitapsepeti.catalog.dto.request.CreateBookRequest;
import com.kitapsepeti.catalog.dto.request.StockAdjustmentRequest;
import com.kitapsepeti.catalog.dto.request.UpdateBookRequest;
import com.kitapsepeti.catalog.dto.response.AdminBookResponse;
import com.kitapsepeti.catalog.dto.response.AdminBookSummaryResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.entity.Author;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.BookStatus;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.exception.BookNotPublishableException;
import com.kitapsepeti.catalog.exception.InvalidFieldException;
import com.kitapsepeti.catalog.exception.StaleVersionException;
import com.kitapsepeti.catalog.exception.StockBelowReservedException;
import com.kitapsepeti.catalog.mapper.AdminBookMapper;
import com.kitapsepeti.catalog.repository.BookRepository;
import com.kitapsepeti.catalog.repository.CategoryRepository;
import com.kitapsepeti.catalog.service.event.BookRemovedEvent;
import com.kitapsepeti.catalog.service.event.BookUpsertedEvent;
import com.kitapsepeti.catalog.storage.CoverStorage;
import com.kitapsepeti.common.error.ResourceNotFoundException;
import com.kitapsepeti.common.outbox.OutboxService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Kitap yönetimi. Kurallar:
 * <ul>
 * <li>Stok/rezerv entity üzerinden yazılmaz; stok yalnızca {@link BookRepository#adjustStock} ile değişir.</li>
 * <li>Olaylar ({@code BookUpserted}/{@code BookRemoved}) iş verisiyle aynı transaction'da outbox'a yazılır;
 * yazımdan sonra (ör. flush'ta) hata olursa ikisi birlikte geri alınır.</li>
 * <li>Değiştiren işlemler kitap satırını kilitleyerek okur ({@link BookRepository#findForUpdateById}).</li>
 * <li>ISBN tekliği DB'de ({@code uk_books_isbn} → 409 ISBN_ALREADY_EXISTS); ön kontrol yapılmaz.</li>
 * </ul>
 * Kitap fiziksel olarak silinmez; silme = arşivleme.
 */
@Service
@Transactional
public class BookAdminService {

	private static final Sort ADMIN_ORDER = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

	private static final Set<String> ALLOWED_COVER_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final BookRepository bookRepository;

	private final AuthorAdminService authorAdminService;

	private final CategoryRepository categoryRepository;

	private final OutboxService outboxService;

	private final BookEventFactory eventFactory;

	private final CoverIngestService coverIngestService;

	private final CoverStorage coverStorage;

	private final S3Properties s3Properties;

	public BookAdminService(BookRepository bookRepository, AuthorAdminService authorAdminService,
			CategoryRepository categoryRepository, OutboxService outboxService, BookEventFactory eventFactory,
			CoverIngestService coverIngestService, CoverStorage coverStorage, S3Properties s3Properties) {
		this.bookRepository = bookRepository;
		this.authorAdminService = authorAdminService;
		this.categoryRepository = categoryRepository;
		this.outboxService = outboxService;
		this.eventFactory = eventFactory;
		this.coverIngestService = coverIngestService;
		this.coverStorage = coverStorage;
		this.s3Properties = s3Properties;
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminBookSummaryResponse> list(AdminBookListRequest request) {
		Specification<Book> spec = (request.status() == null) ? Specification.unrestricted()
				: (root, query, cb) -> cb.equal(root.get("status"), request.status());
		PageRequest pageable = PageRequest.of(request.page(), request.size(), ADMIN_ORDER);
		return PageResponse.of(this.bookRepository.findAll(spec, pageable), AdminBookMapper::toSummary);
	}

	@Transactional(readOnly = true)
	public AdminBookResponse get(UUID id) {
		Book book = this.bookRepository.findWithDetailsById(id).orElseThrow(BookAdminService::notFound);
		return AdminBookMapper.toResponse(book);
	}

	/** Her zaman DRAFT ve TRY; olay yazılmaz (yayında olmayan kitap dışarıya duyurulmaz). */
	public AdminBookResponse create(CreateBookRequest request) {
		List<Author> authors = this.authorAdminService.ensureByNames(request.authorNames());
		List<Category> categories = requireAll(this.categoryRepository, request.categoryIds(), "categoryIds",
				"categories");
		Book book = new Book(request.title(), scaled(request.priceAmount()), request.initialStock());
		book.setIsbn(request.isbn());
		book.setDescription(request.description());
		book.setPageCount(request.pageCount());
		book.setCoverUrl(this.coverIngestService.ingestIfExternal(request.coverUrl()));
		book.getAuthors().addAll(authors);
		book.getCategories().addAll(categories);
		return AdminBookMapper.toResponse(this.bookRepository.saveAndFlush(book));
	}

	/**
	 * İstemcinin versiyonu güncel değilse hiçbir şey yazılmadan 409. Referanslar önce çözülür, sonra alanlar
	 * değişir; kitap yayındaysa olay flush'tan ÖNCE yazılır (flush'ta çıkan hata ikisini birlikte geri alır).
	 */
	public AdminBookResponse update(UUID id, UpdateBookRequest request) {
		Book book = requireForUpdate(id);
		if (!book.getVersion().equals(request.version())) {
			throw new StaleVersionException();
		}
		List<Author> authors = (request.authorNames() != null)
				? this.authorAdminService.ensureByNames(request.authorNames()) : null;
		List<Category> categories = (request.categoryIds() != null)
				? requireAll(this.categoryRepository, request.categoryIds(), "categoryIds", "categories") : null;

		if (request.title() != null) {
			book.setTitle(request.title());
		}
		if (request.isbn() != null) {
			book.setIsbn(blankToNull(request.isbn()));
		}
		if (request.description() != null) {
			book.setDescription(blankToNull(request.description()));
		}
		if (request.pageCount() != null) {
			book.setPageCount(request.pageCount());
		}
		if (request.coverUrl() != null) {
			String cleared = blankToNull(request.coverUrl());
			book.setCoverUrl(cleared == null ? null : this.coverIngestService.ingestIfExternal(cleared));
		}
		if (request.priceAmount() != null) {
			book.setPriceAmount(scaled(request.priceAmount()));
		}
		if (authors != null) {
			replace(book.getAuthors(), authors);
		}
		if (categories != null) {
			replace(book.getCategories(), categories);
		}
		if (book.getStatus() == BookStatus.PUBLISHED) {
			appendUpserted(book);
		}
		this.bookRepository.flush();
		return AdminBookMapper.toResponse(book);
	}

	/**
	 * DRAFT/ARCHIVED → PUBLISHED. En az bir yazar, en az bir kategori ve sıfırdan büyük fiyat gerekir.
	 * {@code publishedAt} yalnızca ilk yayında atanır. Zaten yayındaysa değişiklik ve olay yok.
	 */
	public AdminBookResponse publish(UUID id) {
		Book book = requireForUpdate(id);
		if (book.getStatus() == BookStatus.PUBLISHED) {
			return AdminBookMapper.toResponse(book);
		}
		List<String> missing = new ArrayList<>();
		if (book.getAuthors().isEmpty()) {
			missing.add("at least one author");
		}
		if (book.getCategories().isEmpty()) {
			missing.add("at least one category");
		}
		if (book.getPriceAmount().signum() <= 0) {
			missing.add("a price greater than zero");
		}
		if (!missing.isEmpty()) {
			throw new BookNotPublishableException(missing);
		}
		book.setStatus(BookStatus.PUBLISHED);
		if (book.getPublishedAt() == null) {
			book.setPublishedAt(Instant.now());
		}
		appendUpserted(book);
		this.bookRepository.flush();
		return AdminBookMapper.toResponse(book);
	}

	/**
	 * → ARCHIVED (fiziksel silme yok). Önceki durum PUBLISHED ise {@code BookRemoved}; DRAFT ise olay yok;
	 * zaten arşivdeyse değişiklik ve olay yok. {@code publishedAt} korunur.
	 */
	public AdminBookResponse archive(UUID id) {
		Book book = requireForUpdate(id);
		BookStatus previous = book.getStatus();
		if (previous == BookStatus.ARCHIVED) {
			return AdminBookMapper.toResponse(book);
		}
		book.setStatus(BookStatus.ARCHIVED);
		if (previous == BookStatus.PUBLISHED) {
			this.outboxService.append(BookEventFactory.BOOK_AGGREGATE, book.getId(), BookRemovedEvent.TYPE,
					this.eventFactory.removed(book));
		}
		this.bookRepository.flush();
		return AdminBookMapper.toResponse(book);
	}

	/**
	 * Multipart kapak yükleme. jpeg/png/webp, boyut ≤ {@link S3Properties#maxObjectBytes()}. Yayındaysa
	 * {@code BookUpserted}.
	 */
	public AdminBookResponse uploadCover(UUID id, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new InvalidFieldException("file", "must not be empty");
		}
		String contentType = normalizeMediaType(file.getContentType());
		if (contentType == null || !ALLOWED_COVER_TYPES.contains(contentType)) {
			throw new InvalidFieldException("file", "must be image/jpeg, image/png, or image/webp");
		}
		if (file.getSize() > this.s3Properties.maxObjectBytes()) {
			throw new InvalidFieldException("file", "must not exceed maximum size");
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException ex) {
			throw new InvalidFieldException("file", "could not be read");
		}
		if (bytes.length > this.s3Properties.maxObjectBytes()) {
			throw new InvalidFieldException("file", "must not exceed maximum size");
		}
		Book book = requireForUpdate(id);
		String ext = switch (contentType) {
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			default -> "jpg";
		};
		String key = "covers/" + id + "/" + UUID.randomUUID() + "." + ext;
		book.setCoverUrl(this.coverStorage.put(bytes, contentType, key));
		if (book.getStatus() == BookStatus.PUBLISHED) {
			appendUpserted(book);
		}
		this.bookRepository.flush();
		return AdminBookMapper.toResponse(book);
	}

	/**
	 * Tek koşullu UPDATE; 0 satır etkilenirse kitap yoksa 404, varsa 409 STOCK_BELOW_RESERVED. Yayındaki kitabın
	 * {@code inStock} değeri değiştiyse {@code BookUpserted}. Önceki değer, güncellenmiş satırdan
	 * ({@code available - delta}) hesaplanır: satır bu transaction'da kilitli, araya başka yazma giremez.
	 */
	public AdminBookResponse adjustStock(UUID id, StockAdjustmentRequest request) {
		int delta = request.delta();
		if (delta == 0) {
			throw new InvalidFieldException("delta", "must not be zero");
		}
		if (this.bookRepository.adjustStock(id, delta) == 0) {
			if (!this.bookRepository.existsById(id)) {
				throw notFound();
			}
			throw new StockBelowReservedException();
		}
		Book book = this.bookRepository.findWithDetailsById(id).orElseThrow(BookAdminService::notFound);
		if (book.getStatus() == BookStatus.PUBLISHED) {
			boolean inStockAfter = book.getAvailableQuantity() > 0;
			boolean inStockBefore = book.getAvailableQuantity() - delta > 0;
			if (inStockBefore != inStockAfter) {
				appendUpserted(book);
			}
		}
		return AdminBookMapper.toResponse(book);
	}

	private void appendUpserted(Book book) {
		this.outboxService.append(BookEventFactory.BOOK_AGGREGATE, book.getId(), BookUpsertedEvent.TYPE,
				this.eventFactory.upserted(book));
	}

	private Book requireForUpdate(UUID id) {
		return this.bookRepository.findForUpdateById(id).orElseThrow(BookAdminService::notFound);
	}

	/** Kümedeki her id var olmalı; yoksa alanlı 400 (hangi id'nin eksik olduğu söylenmez). */
	private static <T> List<T> requireAll(JpaRepository<T, UUID> repository, Set<UUID> ids, String field,
			String noun) {
		if (ids.isEmpty()) {
			return List.of();
		}
		List<T> found = repository.findAllById(ids);
		if (found.size() != ids.size()) {
			throw new InvalidFieldException(field, "one or more " + noun + " do not exist");
		}
		return found;
	}

	private static <T> void replace(Collection<T> target, Collection<T> replacement) {
		target.clear();
		target.addAll(replacement);
	}

	/** {@code @Digits(fraction = 2)} doğruladığı için yuvarlama gerekmez; yanıt ve olay hep 2 hane gösterir. */
	private static BigDecimal scaled(BigDecimal amount) {
		return amount.setScale(2, RoundingMode.UNNECESSARY);
	}

	private static String blankToNull(String value) {
		return value.isBlank() ? null : value;
	}

	private static String normalizeMediaType(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String type = raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		if ("image/jpg".equals(type)) {
			return "image/jpeg";
		}
		return type;
	}

	private static ResourceNotFoundException notFound() {
		return new ResourceNotFoundException("Book not found.");
	}

}
