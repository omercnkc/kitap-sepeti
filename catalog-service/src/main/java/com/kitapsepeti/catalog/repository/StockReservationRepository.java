package com.kitapsepeti.catalog.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.ReservationStatus;
import com.kitapsepeti.catalog.entity.StockReservation;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * {@link StockReservation} kayıtlarına erişim.
 */
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

	/** Hibernate'te kilit zaman aşımı -2 = SKIP LOCKED ({@code org.hibernate.Timeouts.SKIP_LOCKED_MILLI}). */
	String SKIP_LOCKED = "-2";

	Optional<StockReservation> findByOrderIdAndBookId(UUID orderId, UUID bookId);

	List<StockReservation> findAllByOrderId(UUID orderId);

	/**
	 * Siparişin satırlarını transaction sonuna kadar kilitler ({@code SELECT ... FOR UPDATE}); kitap id'sine göre
	 * sıralı, böylece ardından gelen kitap UPDATE'leri her transaction'da aynı sırayla kilitlenir.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from StockReservation r where r.orderId = :orderId order by r.book.id")
	List<StockReservation> findAllByOrderIdForUpdate(@Param("orderId") UUID orderId);

	/**
	 * Süresi dolmuş 'held' satırı olan siparişler (kilitsiz okuma), en eski {@code expires_at} önce.
	 * {@code ix_stock_reservations_status_expires (status, expires_at)} aralık taramasıyla okunur.
	 */
	@Query(value = """
			SELECT BIN_TO_UUID(order_id) FROM stock_reservations
			WHERE status = 'held' AND expires_at < :now
			GROUP BY order_id
			ORDER BY MIN(expires_at), order_id
			LIMIT :limit""", nativeQuery = true)
	List<String> findExpiredHeldOrderIds(@Param("now") Instant now, @Param("limit") int limit);

	/**
	 * Siparişin 'held' satırlarını kilitler ({@code FOR UPDATE SKIP LOCKED}); başka bir transaction'ın tuttuğu
	 * satırlar beklenmeden atlanır. Kitap id'sine göre sıralı.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
	@Query("select r from StockReservation r where r.orderId = :orderId and r.status = :status order by r.book.id")
	List<StockReservation> lockByOrderIdAndStatusSkipLocked(@Param("orderId") UUID orderId,
			@Param("status") ReservationStatus status);

	long countByOrderIdAndStatus(UUID orderId, ReservationStatus status);

	/** Siparişin {@code from} durumundaki satırlarını {@code to} yapar; updated_at DB'de güncellenir. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update StockReservation r set r.status = :to where r.orderId = :orderId and r.status = :from")
	int transition(@Param("orderId") UUID orderId, @Param("from") ReservationStatus from,
			@Param("to") ReservationStatus to);

}
