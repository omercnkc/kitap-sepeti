package com.kitapsepeti.payment.repository;

import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	/** Sipariş başına en fazla bir ödeme ({@code uk_payments_order}). */
	Optional<Payment> findByOrderId(UUID orderId);

	/** Webhook'taki sağlayıcı referansıyla ({@code uk_payments_provider_ref}). */
	Optional<Payment> findByProviderTypeAndProviderPaymentId(PaymentProviderType providerType,
			String providerPaymentId);

}
