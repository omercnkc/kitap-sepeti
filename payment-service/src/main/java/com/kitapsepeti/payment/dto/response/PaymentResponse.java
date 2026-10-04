package com.kitapsepeti.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.kitapsepeti.payment.entity.Payment;

/**
 * Ödemenin internal görünümü. userId ve sağlayıcı referansı bilerek yok.
 *
 * @param status {@code initiated} | {@code succeeded} | {@code failed}
 * @param amount JSON sayı, her zaman 2 ondalık (ör. {@code 149.90})
 * @param failureCode yalnızca {@code failed} ödemede dolu
 * @param redirectUrl kullanıcının yönlendirileceği sağlayıcı sayfası; mock sağlayıcı kullanmaz, v1'de her zaman null
 */
public record PaymentResponse(UUID paymentId, UUID orderId, String status, BigDecimal amount, String currency,
		String failureCode, String redirectUrl, Instant createdAt, Instant updatedAt) {

	public static PaymentResponse of(Payment payment) {
		return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getStatus().dbValue(),
				payment.getAmount(), payment.getCurrency(), payment.getFailureCode(), null, payment.getCreatedAt(),
				payment.getUpdatedAt());
	}

}
