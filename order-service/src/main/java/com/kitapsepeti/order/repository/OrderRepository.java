package com.kitapsepeti.order.repository;

import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Sipariş aggregate'i. Kalem ve geçmiş satırlarının ayrı repository'si yok: {@link Order} üzerinden cascade ile yazılır,
 * sipariş yüklenince ilişkiden okunur.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

	/**
	 * Değiştirme: sipariş satırı {@code FOR UPDATE} ile, kalemler yüklenmeden. Aynı siparişe eşzamanlı geçişler (ödeme
	 * sonucu, zaman aşımı, stok yanıtı) sıraya girer. Transaction içinde çağrılmalı.
	 * <p>
	 * Bekleme üst sınırı bağlantının {@code innodb_lock_wait_timeout}'u (5 sn, application.yml
	 * {@code connection-init-sql}); aşılırsa {@code PessimisticLockingFailureException}.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Order o where o.id = :id")
	Optional<Order> findByIdForUpdate(@Param("id") UUID id);

	/**
	 * Okuma: sahibinin siparişi, kalemleriyle tek sorguda. Başka kullanıcının siparişi bulunmaz (varlığı da sızmaz).
	 * Kilitsiz.
	 */
	@EntityGraph(attributePaths = "items")
	Optional<Order> findByIdAndUserId(UUID id, UUID userId);

	/** Kullanıcının bekleyen siparişi; en fazla bir tane ({@code uk_orders_pending_user}). Kilitsiz. */
	default Optional<Order> findPendingByUserId(UUID userId) {
		return findByUserIdAndStatus(userId, OrderStatus.PENDING);
	}

	Optional<Order> findByUserIdAndStatus(UUID userId, OrderStatus status);

	/**
	 * StockSyncJob için kurtarılacak aday siparişleri getirir:
	 * (status='paid' AND stock_state='held') OR (status='failed' AND stock_state IN ('requested','held')),
	 * updated_at < :cutoff, updated_at sırası, LIMIT batch.
	 * (stock_state, updated_at) indeksini kullanır.
	 */
	@Query("""
			select new com.kitapsepeti.order.repository.StockSyncCandidate(o.id, o.status, o.stockState)
			from Order o
			where ((o.status = :paid and o.stockState = :held)
			    or (o.status = :failed and o.stockState in (:requested, :held)))
			  and o.updatedAt < :cutoff
			order by o.updatedAt asc
			""")
	java.util.List<StockSyncCandidate> findStockSyncCandidates(
			@Param("paid") com.kitapsepeti.order.entity.OrderStatus paid,
			@Param("held") com.kitapsepeti.order.entity.StockState held,
			@Param("failed") com.kitapsepeti.order.entity.OrderStatus failed,
			@Param("requested") com.kitapsepeti.order.entity.StockState requested,
			@Param("cutoff") java.time.Instant cutoff,
			org.springframework.data.domain.Pageable pageable);

	default java.util.List<StockSyncCandidate> findStockSyncCandidates(java.time.Instant cutoff, org.springframework.data.domain.Pageable pageable) {
		return findStockSyncCandidates(com.kitapsepeti.order.entity.OrderStatus.PAID,
				com.kitapsepeti.order.entity.StockState.HELD,
				com.kitapsepeti.order.entity.OrderStatus.FAILED,
				com.kitapsepeti.order.entity.StockState.REQUESTED,
				cutoff, pageable);
	}

}
