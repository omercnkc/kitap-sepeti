package com.kitapsepeti.catalog.service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kitapsepeti.catalog.dto.response.ReservationResponse;
import com.kitapsepeti.catalog.entity.Book;
import com.kitapsepeti.catalog.entity.BookStatus;
import com.kitapsepeti.catalog.entity.ReservationStatus;
import com.kitapsepeti.catalog.entity.StockReservation;
import com.kitapsepeti.catalog.exception.ErrorCode;
import com.kitapsepeti.catalog.exception.ReservationCommittedException;
import com.kitapsepeti.catalog.exception.ReservationReleasedException;
import com.kitapsepeti.catalog.exception.ResourceNotFoundException;
import com.kitapsepeti.catalog.exception.StockUnavailableException;
import com.kitapsepeti.catalog.repository.BookRepository;
import com.kitapsepeti.catalog.repository.StockReservationRepository;
import com.kitapsepeti.catalog.service.event.BookUpsertedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rezervasyon işlemlerinin transaction'ları; yalnızca {@link StockReservationService} çağırır.
 * <ul>
 * <li>Stok/rezerv yalnızca {@link BookRepository}'deki tek koşullu UPDATE'lerle değişir; versiyon artmaz.</li>
 * <li>Kitap satırları her zaman {@link #LOCK_ORDER} sırasıyla kilitlenir; iki sipariş aynı kitapları ters
 * sırayla istese de birbirini beklerken kilitlenmez (deadlock).</li>
 * <li>READ COMMITTED: {@code FOR UPDATE} yalnızca bulunan satırları kilitler (gap lock yok); böylece onay/iptalin
 * kilitlediği aralık başka bir siparişin rezervasyon INSERT'ini bekletmez.</li>
 * <li>Yayındaki kitabın {@code inStock} değeri değişirse aynı transaction'da {@code BookUpserted} yazılır.</li>
 * </ul>
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class StockReservationTransactions {

	/** DB'deki BINARY(16) sırasıyla (işaretsiz, bayt bayt) aynı; {@code UUID.compareTo} işaretli karşılaştırır. */
	static final Comparator<UUID> LOCK_ORDER = Comparator
		.comparing(UUID::getMostSignificantBits, Long::compareUnsigned)
		.thenComparing(UUID::getLeastSignificantBits, Long::compareUnsigned);

	/** Süresi dolmamış veya başka transaction'da işlenen sipariş için {@link #releaseExpired} sonucu. */
	private static final int NOT_RELEASED = 0;

	private final StockReservationRepository reservationRepository;

	private final BookRepository bookRepository;

	private final OutboxService outboxService;

	private final BookEventFactory eventFactory;

	private final StockProperties properties;

	private final Clock clock;

	public StockReservationTransactions(StockReservationRepository reservationRepository,
			BookRepository bookRepository, OutboxService outboxService, BookEventFactory eventFactory,
			StockProperties properties, Clock clock) {
		this.reservationRepository = reservationRepository;
		this.bookRepository = bookRepository;
		this.outboxService = outboxService;
		this.eventFactory = eventFactory;
		this.properties = properties;
		this.clock = clock;
	}

	/** Siparişin mevcut rezervasyonu; satır yoksa boş. */
	@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
	public Optional<ReservationResponse> find(UUID orderId) {
		List<Line> lines = linesOf(this.reservationRepository.findAllByOrderId(orderId));
		return lines.isEmpty() ? Optional.empty() : Optional.of(response(orderId, lines));
	}

	/**
	 * Ya hep ya hiç: her kalem denenir, hatalılar toplanır; en az biri hatalıysa exception fırlatılır ve
	 * transaction tamamen geri alınır. Aynı siparişin satırları zaten varsa (eşzamanlı istek) INSERT
	 * {@code uk_stock_reservations_order_book} ihlaliyle düşer; çağıran bunu idempotent okumaya çevirir.
	 * @param items bookId → adet, {@link #LOCK_ORDER} sıralı
	 */
	public ReservationResponse reserveNew(UUID orderId, SortedMap<UUID, Integer> items) {
		Instant expiresAt = this.clock.instant().plus(this.properties.reservationTtl()).truncatedTo(ChronoUnit.MICROS);
		List<UUID> failed = new ArrayList<>();
		items.forEach((bookId, quantity) -> {
			if (this.bookRepository.reserve(bookId, quantity, BookStatus.PUBLISHED) == 0) {
				failed.add(bookId);
			}
		});
		if (!failed.isEmpty()) {
			throw unavailable(failed);
		}
		this.reservationRepository.saveAllAndFlush(items.entrySet()
			.stream()
			.map(item -> new StockReservation(this.bookRepository.getReferenceById(item.getKey()), orderId,
					item.getValue(), expiresAt))
			.toList());

		Map<UUID, Book> books = booksById(items.keySet());
		items.forEach((bookId, quantity) -> {
			Book book = books.get(bookId);
			appendIfInStockChanged(book, book.getAvailableQuantity() + quantity);
		});
		List<Line> lines = items.entrySet()
			.stream()
			.map(item -> new Line(item.getKey(), item.getValue(), ReservationStatus.HELD, expiresAt))
			.toList();
		return response(orderId, lines, books);
	}

	/**
	 * 'held' satırları stoktan kalıcı düşer; süresi geçmiş olsa bile. Hepsi zaten onaylıysa değişiklik yok.
	 * Satılabilir adet değişmediği için olay yazılmaz.
	 */
	public ReservationResponse commit(UUID orderId) {
		List<Line> lines = lockedLines(orderId);
		if (allIn(lines, ReservationStatus.COMMITTED)) {
			return response(orderId, lines);
		}
		if (anyIn(lines, ReservationStatus.RELEASED)) {
			throw new ReservationReleasedException();
		}
		for (Line line : held(lines)) {
			if (this.bookRepository.commitReserved(line.bookId(), line.quantity()) != 1) {
				throw inconsistent("commit", orderId, line.bookId());
			}
		}
		this.reservationRepository.transition(orderId, ReservationStatus.HELD, ReservationStatus.COMMITTED);
		return response(orderId, withStatus(lines, ReservationStatus.COMMITTED));
	}

	/** 'held' satırların rezervini geri verir. Hepsi zaten serbestse değişiklik yok. */
	public ReservationResponse release(UUID orderId) {
		List<Line> lines = lockedLines(orderId);
		if (allIn(lines, ReservationStatus.RELEASED)) {
			return response(orderId, lines);
		}
		if (anyIn(lines, ReservationStatus.COMMITTED)) {
			throw new ReservationCommittedException();
		}
		List<Line> held = held(lines);
		Map<UUID, Book> books = releaseHeld(orderId, held);
		List<Line> released = withStatus(lines, ReservationStatus.RELEASED);
		return (held.size() == lines.size()) ? response(orderId, released, books) : response(orderId, released);
	}

	/**
	 * Süre dolumu görevi için: siparişin süresi dolmuş 'held' satırlarını serbest bırakır. Satırlar
	 * {@code SKIP LOCKED} ile kilitlenir; biri bile başka bir transaction'da (onay/iptal) kilitliyse sipariş bu turda
	 * atlanır. Kilitten sonra süre yeniden kontrol edilir.
	 * @return serbest bırakılan satır sayısı; atlandıysa 0
	 */
	public int releaseExpired(UUID orderId, Instant now) {
		List<Line> held = linesOf(
				this.reservationRepository.lockByOrderIdAndStatusSkipLocked(orderId, ReservationStatus.HELD));
		if (held.isEmpty()) {
			return NOT_RELEASED;
		}
		// Kısmi kilit: onay/iptal satırların bir kısmını almış olabilir; kısmen bırakmak siparişi karışık duruma sokar.
		if (held.size() != this.reservationRepository.countByOrderIdAndStatus(orderId, ReservationStatus.HELD)) {
			return NOT_RELEASED;
		}
		if (held.stream().anyMatch(line -> !line.expiresAt().isBefore(now))) {
			return NOT_RELEASED;
		}
		releaseHeld(orderId, held);
		return held.size();
	}

	/** Süresi dolmuş 'held' satırı olan siparişler (kilitsiz), en eski önce. */
	@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
	public List<UUID> findExpiredOrderIds(Instant now, int limit) {
		return this.reservationRepository.findExpiredHeldOrderIds(now, limit).stream().map(UUID::fromString).toList();
	}

	/**
	 * İptal ucunun ve süre dolumu görevinin ortak işi. {@code held} satırları çağıran kilitlemiş olmalı ve
	 * {@link #LOCK_ORDER} sıralı olmalı. Rezerv koşullu UPDATE ile geri verilir (rezerv adetten azsa
	 * {@code IllegalStateException}), satırlar 'released' olur, yayındaki kitabın inStock'u değiştiyse
	 * {@code BookUpserted} yazılır.
	 * @return siparişin kitapları (güncel halleriyle)
	 */
	private Map<UUID, Book> releaseHeld(UUID orderId, List<Line> held) {
		for (Line line : held) {
			if (this.bookRepository.releaseReserved(line.bookId(), line.quantity()) != 1) {
				throw inconsistent("release", orderId, line.bookId());
			}
		}
		this.reservationRepository.transition(orderId, ReservationStatus.HELD, ReservationStatus.RELEASED);

		Map<UUID, Book> books = booksById(held.stream().map(Line::bookId).toList());
		for (Line line : held) {
			Book book = books.get(line.bookId());
			appendIfInStockChanged(book, book.getAvailableQuantity() - line.quantity());
		}
		return books;
	}

	/** Satır yoksa 404. Satırlar kilitli; kitaplar dönen sırayla (LOCK_ORDER) güncellenmeli. */
	private List<Line> lockedLines(UUID orderId) {
		List<Line> lines = linesOf(this.reservationRepository.findAllByOrderIdForUpdate(orderId));
		if (lines.isEmpty()) {
			throw new ResourceNotFoundException("Reservation not found.");
		}
		return lines;
	}

	/**
	 * Hatalı kalemlerin nedeni ayrı okumayla belirlenir (stok kontrolü değil; UPDATE zaten reddetti). Kitap yok
	 * veya yayında değilse BOOK_NOT_AVAILABLE, değilse INSUFFICIENT_STOCK. İkisi birden varsa BOOK_NOT_AVAILABLE
	 * döner ve {@code bookIds} yalnızca o koddaki kitapları içerir.
	 */
	private StockUnavailableException unavailable(List<UUID> failed) {
		Map<UUID, Book> books = booksById(failed);
		List<UUID> notAvailable = failed.stream()
			.filter(id -> !books.containsKey(id) || books.get(id).getStatus() != BookStatus.PUBLISHED)
			.toList();
		if (!notAvailable.isEmpty()) {
			return new StockUnavailableException(ErrorCode.BOOK_NOT_AVAILABLE, notAvailable);
		}
		return new StockUnavailableException(ErrorCode.INSUFFICIENT_STOCK, failed);
	}

	private void appendIfInStockChanged(Book book, int availableBefore) {
		if (book.getStatus() == BookStatus.PUBLISHED && (availableBefore > 0) != (book.getAvailableQuantity() > 0)) {
			this.outboxService.append(BookEventFactory.BOOK_AGGREGATE, book.getId(), BookUpsertedEvent.TYPE,
					this.eventFactory.upserted(book));
		}
	}

	/** Bu durum hiç olmamalı (rezerv, held satırların toplamından az); 500 + ERROR log, transaction geri alınır. */
	private static IllegalStateException inconsistent(String operation, UUID orderId, UUID bookId) {
		return new IllegalStateException("Reserved quantity is lower than the held reservation during " + operation
				+ ": orderId=" + orderId + ", bookId=" + bookId);
	}

	private Map<UUID, Book> booksById(Iterable<UUID> ids) {
		return this.bookRepository.findAllById(ids)
			.stream()
			.collect(Collectors.toMap(Book::getId, Function.identity()));
	}

	private ReservationResponse response(UUID orderId, List<Line> lines) {
		return response(orderId, lines, booksById(lines.stream().map(Line::bookId).toList()));
	}

	/** Satırlar aynı işlemde birlikte değiştiği için durum ve süre ortaktır; durumlar karışıksa "mixed" (olmamalı). */
	private static ReservationResponse response(UUID orderId, List<Line> lines, Map<UUID, Book> books) {
		List<ReservationStatus> statuses = lines.stream().map(Line::status).distinct().toList();
		String status = (statuses.size() == 1) ? statuses.getFirst().name().toLowerCase(Locale.ROOT) : "mixed";
		Instant expiresAt = lines.stream().map(Line::expiresAt).min(Comparator.naturalOrder()).orElseThrow();
		List<ReservationResponse.Item> items = lines.stream().map(line -> {
			Book book = books.get(line.bookId());
			return new ReservationResponse.Item(line.bookId(), book.getTitle(), line.quantity(),
					book.getPriceAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(), book.getCurrency());
		}).toList();
		return new ReservationResponse(orderId, status, expiresAt, items);
	}

	/** Entity'ler toplu UPDATE'lerdeki temizlikten etkilenmesin diye düz değerlere kopyalanır; LOCK_ORDER sıralı. */
	private static List<Line> linesOf(List<StockReservation> rows) {
		return rows.stream()
			.map(row -> new Line(row.getBook().getId(), row.getQuantity(), row.getStatus(), row.getExpiresAt()))
			.sorted(Comparator.comparing(Line::bookId, LOCK_ORDER))
			.toList();
	}

	private static List<Line> held(List<Line> lines) {
		return lines.stream().filter(line -> line.status() == ReservationStatus.HELD).toList();
	}

	private static List<Line> withStatus(List<Line> lines, ReservationStatus status) {
		return lines.stream().map(line -> new Line(line.bookId(), line.quantity(), status, line.expiresAt())).toList();
	}

	private static boolean allIn(List<Line> lines, ReservationStatus status) {
		return lines.stream().allMatch(line -> line.status() == status);
	}

	private static boolean anyIn(List<Line> lines, ReservationStatus status) {
		return lines.stream().anyMatch(line -> line.status() == status);
	}

	private record Line(UUID bookId, int quantity, ReservationStatus status, Instant expiresAt) {
	}

}
