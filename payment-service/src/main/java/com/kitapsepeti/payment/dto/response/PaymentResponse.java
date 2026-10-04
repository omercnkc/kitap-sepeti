package com.kitapsepeti.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.kitapsepeti.payment.config.OpenApiConfig;
import com.kitapsepeti.payment.entity.Payment;
import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Ödemenin internal görünümü. userId ve sağlayıcı referansı bilerek yok.
 *
 * @param status {@code initiated} | {@code succeeded} | {@code failed}
 * @param amount JSON sayı, her zaman 2 ondalık (ör. {@code 149.90})
 * @param failureCode yalnızca {@code failed} ödemede dolu
 * @param redirectUrl kullanıcının yönlendirileceği sağlayıcı sayfası; mock sağlayıcı kullanmaz, v1'de her zaman null
 */
@Schema(description = "Ödeme. Tüm alanlar her yanıtta bulunur; boş olanlar `null` gelir.")
public record PaymentResponse(
		@Schema(requiredMode = REQUIRED, description = "Ödemenin id'si.") UUID paymentId,
		@Schema(requiredMode = REQUIRED, description = "Siparişin id'si.") UUID orderId,
		@Schema(requiredMode = REQUIRED, ref = OpenApiConfig.PAYMENT_STATUS_SCHEMA_REF) String status,
		@Schema(requiredMode = REQUIRED, description = "Tutar, 2 ondalık basamak.") BigDecimal amount,
		@Schema(requiredMode = REQUIRED, description = "ISO 4217 para birimi.", example = "TRY") String currency,
		@Schema(requiredMode = REQUIRED, types = { "string", "null" },
				description = "Sağlayıcının ret kodu; yalnızca `failed` ödemede dolu, diğerlerinde null.",
				example = "CARD_DECLINED") String failureCode,
		@Schema(requiredMode = REQUIRED, types = { "string", "null" },
				description = "Kullanıcının yönlendirileceği sağlayıcı sayfası. v1'de her zaman null.")
		String redirectUrl,
		@Schema(requiredMode = REQUIRED, description = "Oluşturulma zamanı (UTC).") Instant createdAt,
		@Schema(requiredMode = REQUIRED, description = "Son değişiklik zamanı (UTC).") Instant updatedAt) {

	public static PaymentResponse of(Payment payment) {
		return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getStatus().dbValue(),
				payment.getAmount(), payment.getCurrency(), payment.getFailureCode(), null, payment.getCreatedAt(),
				payment.getUpdatedAt());
	}

}
