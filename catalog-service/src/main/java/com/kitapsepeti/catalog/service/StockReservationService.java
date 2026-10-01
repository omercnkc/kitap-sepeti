package com.kitapsepeti.catalog.service;

import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import com.kitapsepeti.catalog.dto.request.ReserveStockRequest;
import com.kitapsepeti.catalog.dto.response.ReservationResponse;
import com.kitapsepeti.catalog.exception.InvalidFieldException;
import com.kitapsepeti.catalog.exception.ReservationMismatchException;
import com.kitapsepeti.catalog.exception.StockUnavailableException;
import com.kitapsepeti.common.error.DbConstraints;
import com.kitapsepeti.common.error.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Sipariş stok rezervasyonları (internal uçlar).
 * <p>
 * Bilerek transactional DEĞİL: yeni rezervasyon transaction'ı aynı siparişin eşzamanlı isteğiyle çakışırsa
 * ({@code uk_stock_reservations_order_book}) tamamen geri alınır, ardından mevcut rezervasyon AYRI bir
 * transaction'da okunup idempotent yanıt verilir. Asıl işler {@link StockReservationTransactions}'ta.
 */
@Service
public class StockReservationService {

	private static final String ORDER_BOOK_UNIQUE = "uk_stock_reservations_order_book";

	private final StockReservationTransactions transactions;

	public StockReservationService(StockReservationTransactions transactions) {
		this.transactions = transactions;
	}

	/**
	 * Siparişin rezervasyonu yoksa oluşturur ({@code created = true}). Varsa: aynı (bookId, adet) kümesi ise
	 * değişiklik yapmadan mevcut hali döner, farklıysa RESERVATION_MISMATCH.
	 */
	public ReserveResult reserve(ReserveStockRequest request) {
		UUID orderId = request.orderId();
		SortedMap<UUID, Integer> items = itemsOf(request);

		Optional<ReservationResponse> existing = this.transactions.find(orderId);
		if (existing.isPresent()) {
			return replay(existing.get(), items);
		}
		try {
			return new ReserveResult(this.transactions.reserveNew(orderId, items), true);
		}
		catch (DataIntegrityViolationException ex) {
			if (!DbConstraints.isViolated(ex, ORDER_BOOK_UNIQUE)) {
				throw ex;
			}
			return replay(this.transactions.find(orderId).orElseThrow(() -> ex), items);
		}
		catch (StockUnavailableException ex) {
			// Eşzamanlı aynı istek önce kazanıp stoğu tüketmiş olabilir; o zaman yanıt onun rezervasyonudur.
			Optional<ReservationResponse> concurrent = this.transactions.find(orderId);
			if (concurrent.isEmpty()) {
				throw ex;
			}
			return replay(concurrent.get(), items);
		}
	}

	public ReservationResponse commit(UUID orderId) {
		return this.transactions.commit(orderId);
	}

	public ReservationResponse release(UUID orderId) {
		return this.transactions.release(orderId);
	}

	public ReservationResponse get(UUID orderId) {
		return this.transactions.find(orderId).orElseThrow(() -> new ResourceNotFoundException("Reservation not found."));
	}

	private static ReserveResult replay(ReservationResponse existing, Map<UUID, Integer> requested) {
		Map<UUID, Integer> reserved = existing.items()
			.stream()
			.collect(Collectors.toMap(ReservationResponse.Item::bookId, ReservationResponse.Item::quantity));
		if (!reserved.equals(requested)) {
			throw new ReservationMismatchException();
		}
		return new ReserveResult(existing, false);
	}

	private static SortedMap<UUID, Integer> itemsOf(ReserveStockRequest request) {
		SortedMap<UUID, Integer> items = new TreeMap<>(StockReservationTransactions.LOCK_ORDER);
		for (ReserveStockRequest.Item item : request.items()) {
			if (items.put(item.bookId(), item.quantity()) != null) {
				throw new InvalidFieldException("items", "must not contain the same bookId more than once");
			}
		}
		return items;
	}

	/** @param created yeni oluşturulduysa true (201), mevcut rezervasyon döndüyse false (200) */
	public record ReserveResult(ReservationResponse reservation, boolean created) {
	}

}
