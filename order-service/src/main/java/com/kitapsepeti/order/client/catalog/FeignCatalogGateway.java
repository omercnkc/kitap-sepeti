package com.kitapsepeti.order.client.catalog;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.order.client.CallOutcome;
import com.kitapsepeti.order.client.Downstream;
import com.kitapsepeti.order.client.InvalidResponseException;
import com.kitapsepeti.order.client.RemoteCalls;
import com.kitapsepeti.order.gateway.BookLookupResult;
import com.kitapsepeti.order.gateway.CatalogBook;
import com.kitapsepeti.order.gateway.CatalogGateway;
import com.kitapsepeti.order.gateway.CommitResult;
import com.kitapsepeti.order.gateway.NotPerformed;
import com.kitapsepeti.order.gateway.Rejected;
import com.kitapsepeti.order.gateway.ReleaseResult;
import com.kitapsepeti.order.gateway.ReservationStatus;
import com.kitapsepeti.order.gateway.ReserveResult;
import com.kitapsepeti.order.gateway.StockLine;
import com.kitapsepeti.order.gateway.Unavailable;
import com.kitapsepeti.order.gateway.Unknown;
import org.springframework.stereotype.Component;

/**
 * Catalog sözleşmesinin (docs/api/catalog-internal-stock.md) gateway sonuçlarına eşlemesi. Rezervasyon yanıtları
 * yalnızca istenen siparişe ve işlemin beklediği duruma aitse kabul edilir; aksi sözleşme ihlali ({@link Unknown}).
 */
@Component
public class FeignCatalogGateway implements CatalogGateway {

	/** Catalog {@code ReserveStockItem.quantity} üst sınırı. */
	public static final int MAX_QUANTITY = 100;

	static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";

	static final String BOOK_NOT_AVAILABLE = "BOOK_NOT_AVAILABLE";

	static final String RESERVATION_RELEASED = "RESERVATION_RELEASED";

	static final String RESERVATION_COMMITTED = "RESERVATION_COMMITTED";

	static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";

	private final CatalogClient client;

	private final RemoteCalls calls;

	public FeignCatalogGateway(CatalogClient client, RemoteCalls calls) {
		this.client = client;
		this.calls = calls;
	}

	@Override
	public BookLookupResult lookup(Collection<UUID> bookIds) {
		Set<UUID> requested = new LinkedHashSet<>(bookIds);
		if (requested.contains(null)) {
			throw new IllegalArgumentException("bookIds must not contain null");
		}
		if (requested.isEmpty()) {
			return new BookLookupResult.Found(Map.of(), Set.of());
		}
		if (requested.size() > MAX_BOOKS) {
			throw new IllegalArgumentException("At most " + MAX_BOOKS + " books can be looked up at once");
		}
		return this.calls.execute(Downstream.CATALOG, "lookup", () -> this.client.lookup(List.copyOf(requested)),
				FeignCatalogGateway::validateLookup, outcome -> toLookupResult(outcome, requested));
	}

	@Override
	public ReserveResult reserve(UUID orderId, List<StockLine> lines) {
		Objects.requireNonNull(orderId, "orderId");
		if (lines.isEmpty() || lines.size() > MAX_BOOKS) {
			throw new IllegalArgumentException("A reservation needs 1-" + MAX_BOOKS + " lines");
		}
		Set<UUID> books = new HashSet<>();
		for (StockLine line : lines) {
			if (!books.add(line.bookId())) {
				throw new IllegalArgumentException("A reservation cannot repeat a book");
			}
			if (line.quantity() < 1 || line.quantity() > MAX_QUANTITY) {
				throw new IllegalArgumentException("Reservation quantity must be 1-" + MAX_QUANTITY);
			}
		}
		ReserveStockRequest request = new ReserveStockRequest(orderId,
				lines.stream().map(line -> new ReserveStockRequest.Item(line.bookId(), line.quantity())).toList());
		return this.calls.execute(Downstream.CATALOG, "reserve", () -> this.client.reserve(request),
				body -> status(body, orderId), FeignCatalogGateway::toReserveResult);
	}

	@Override
	public CommitResult commit(UUID orderId) {
		Objects.requireNonNull(orderId, "orderId");
		return this.calls.execute(Downstream.CATALOG, "commit", () -> this.client.commit(orderId),
				body -> expect(body, orderId, ReservationStatus.COMMITTED), FeignCatalogGateway::toCommitResult);
	}

	@Override
	public ReleaseResult release(UUID orderId) {
		Objects.requireNonNull(orderId, "orderId");
		return this.calls.execute(Downstream.CATALOG, "release", () -> this.client.release(orderId),
				body -> expect(body, orderId, ReservationStatus.RELEASED), FeignCatalogGateway::toReleaseResult);
	}

	private static void validateLookup(BookLookupResponse body) {
		Set<UUID> seen = new HashSet<>();
		for (BookLookupResponse.Book book : body.items()) {
			if (!seen.add(book.id())) {
				throw new InvalidResponseException("Catalog lookup repeats a book");
			}
		}
	}

	private static BookLookupResult toLookupResult(CallOutcome<BookLookupResponse> outcome, Set<UUID> requested) {
		if (!(outcome instanceof CallOutcome.Success<BookLookupResponse> success)) {
			return new Unavailable();
		}
		Map<UUID, CatalogBook> books = new HashMap<>();
		for (BookLookupResponse.Book book : success.body().items()) {
			if (requested.contains(book.id())) {
				books.put(book.id(),
						new CatalogBook(book.id(), book.title(), book.priceAmount(), book.currency(), book.inStock()));
			}
		}
		Set<UUID> notFound = new HashSet<>(requested);
		notFound.removeAll(books.keySet());
		return new BookLookupResult.Found(books, notFound);
	}

	private static ReservationStatus status(ReservationResponse body, UUID orderId) {
		if (!orderId.equals(body.orderId())) {
			throw new InvalidResponseException("Catalog reservation belongs to another order");
		}
		return switch (body.status()) {
			case "held" -> ReservationStatus.HELD;
			case "committed" -> ReservationStatus.COMMITTED;
			case "released" -> ReservationStatus.RELEASED;
			default -> throw new InvalidResponseException("Catalog reservation status is not recognised");
		};
	}

	private static void expect(ReservationResponse body, UUID orderId, ReservationStatus expected) {
		if (status(body, orderId) != expected) {
			throw new InvalidResponseException("Catalog reservation is not in the expected state");
		}
	}

	private static ReserveResult toReserveResult(CallOutcome<ReservationResponse> outcome) {
		return switch (outcome) {
			case CallOutcome.Success<ReservationResponse> success -> {
				ReservationStatus status = status(success.body(), success.body().orderId());
				yield (status == ReservationStatus.HELD) ? new ReserveResult.Reserved(success.body().expiresAt())
						: new ReserveResult.NotHeld(status);
			}
			case CallOutcome.Problem<ReservationResponse> problem when is(problem, 409, INSUFFICIENT_STOCK) ->
				new ReserveResult.Insufficient(problem.bookIds());
			case CallOutcome.Problem<ReservationResponse> problem when is(problem, 409, BOOK_NOT_AVAILABLE) ->
				new ReserveResult.NotSellable(problem.bookIds());
			case CallOutcome.Problem<ReservationResponse> problem -> new Rejected(problem.status(), problem.code());
			case CallOutcome.NotSent<ReservationResponse> notSent -> new NotPerformed();
			case CallOutcome.Failed<ReservationResponse> failed -> new Unknown();
		};
	}

	private static CommitResult toCommitResult(CallOutcome<ReservationResponse> outcome) {
		return switch (outcome) {
			case CallOutcome.Success<ReservationResponse> success -> new CommitResult.Committed();
			case CallOutcome.Problem<ReservationResponse> problem when is(problem, 409, RESERVATION_RELEASED) ->
				new CommitResult.AlreadyReleased();
			case CallOutcome.Problem<ReservationResponse> problem -> new Rejected(problem.status(), problem.code());
			case CallOutcome.NotSent<ReservationResponse> notSent -> new NotPerformed();
			case CallOutcome.Failed<ReservationResponse> failed -> new Unknown();
		};
	}

	private static ReleaseResult toReleaseResult(CallOutcome<ReservationResponse> outcome) {
		return switch (outcome) {
			case CallOutcome.Success<ReservationResponse> success -> new ReleaseResult.Released();
			case CallOutcome.Problem<ReservationResponse> problem when is(problem, 404, RESOURCE_NOT_FOUND) ->
				new ReleaseResult.Released();
			case CallOutcome.Problem<ReservationResponse> problem when is(problem, 409, RESERVATION_COMMITTED) ->
				new ReleaseResult.AlreadyCommitted();
			case CallOutcome.Problem<ReservationResponse> problem -> new Rejected(problem.status(), problem.code());
			case CallOutcome.NotSent<ReservationResponse> notSent -> new NotPerformed();
			case CallOutcome.Failed<ReservationResponse> failed -> new Unknown();
		};
	}

	private static boolean is(CallOutcome.Problem<?> problem, int status, String code) {
		return problem.status() == status && code.equals(problem.code());
	}

}
