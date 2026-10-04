package com.kitapsepeti.payment.repository;

import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	/** Sipariş başına en fazla bir ödeme ({@code uk_payments_order}). Kilitsiz. */
	Optional<Payment> findByOrderId(UUID orderId);

	/**
	 * Webhook'taki sağlayıcı referansıyla ({@code uk_payments_provider_ref}); referans büyük/küçük harfe duyarlı
	 * karşılaştırılır (kolon {@code utf8mb4_bin}, V2).
	 */
	Optional<Payment> findByProviderTypeAndProviderPaymentId(PaymentProviderType providerType,
			String providerPaymentId);

	/**
	 * Ödeme satırı {@code FOR UPDATE} ile; aynı ödemeye eşzamanlı referans yazımları sıraya girer. Bekleme en fazla
	 * {@code innodb_lock_wait_timeout} (5 sn, Hikari {@code connection-init-sql}).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.id = :id")
	Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

}
