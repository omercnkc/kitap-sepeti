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

}
