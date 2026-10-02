package com.kitapsepeti.cart.repository;

import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.cart.entity.Cart;
import com.kitapsepeti.cart.entity.CartStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Sepet aggregate'i. Satırların ayrı repository'si yok: satırlar {@link Cart} üzerinden yönetilir.
 */
public interface CartRepository extends JpaRepository<Cart, UUID> {

	/** Okuma: sepet ve satırları tek sorguda (satırlar eklenme sırasıyla). */
	@EntityGraph(attributePaths = "items")
	Optional<Cart> findByUserIdAndStatus(UUID userId, CartStatus status);

	/**
	 * Değiştirme: kullanıcının aktif sepeti {@code FOR UPDATE} ile, satırlar yüklenmeden. Aynı kullanıcının eşzamanlı
	 * istekleri bu satırda sıraya girer. Transaction içinde çağrılmalı.
	 * <p>
	 * Bekleme üst sınırı bağlantının {@code innodb_lock_wait_timeout}'u (application.yml {@code connection-init-sql});
	 * aşılırsa {@code PessimisticLockingFailureException}. {@code jakarta.persistence.lock.timeout} ipucu MySQL'de
	 * pozitif değerlerde etkisiz (Hibernate SQL'e yazamıyor, bağlantıya da uygulamıyor), bu yüzden kullanılmaz.
	 */
	default Optional<Cart> findActiveByUserIdForUpdate(UUID userId) {
		return lockByUserIdAndStatus(userId, CartStatus.ACTIVE);
	}

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Cart c where c.userId = :userId and c.status = :status")
	Optional<Cart> lockByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") CartStatus status);

}
