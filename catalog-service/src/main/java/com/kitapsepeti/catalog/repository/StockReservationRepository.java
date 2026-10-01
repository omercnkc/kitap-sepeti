package com.kitapsepeti.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.ReservationStatus;
import com.kitapsepeti.catalog.entity.StockReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link StockReservation} kayıtlarına erişim.
 */
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

	Optional<StockReservation> findByOrderIdAndBookId(UUID orderId, UUID bookId);

	List<StockReservation> findAllByOrderId(UUID orderId);

	/**
	 * Siparişin satırlarını transaction sonuna kadar kilitler ({@code SELECT ... FOR UPDATE}); kitap id'sine göre
	 * sıralı, böylece ardından gelen kitap UPDATE'leri her transaction'da aynı sırayla kilitlenir.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from StockReservation r where r.orderId = :orderId order by r.book.id")
	List<StockReservation> findAllByOrderIdForUpdate(@Param("orderId") UUID orderId);

	/** Siparişin {@code from} durumundaki satırlarını {@code to} yapar; updated_at DB'de güncellenir. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update StockReservation r set r.status = :to where r.orderId = :orderId and r.status = :from")
	int transition(@Param("orderId") UUID orderId, @Param("from") ReservationStatus from,
			@Param("to") ReservationStatus to);

}
