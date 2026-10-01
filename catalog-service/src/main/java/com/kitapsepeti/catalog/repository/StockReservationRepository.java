package com.kitapsepeti.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link StockReservation} kayıtlarına erişim.
 */
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

	Optional<StockReservation> findByOrderIdAndBookId(UUID orderId, UUID bookId);

	List<StockReservation> findAllByOrderId(UUID orderId);

}
