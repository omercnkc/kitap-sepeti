package com.kitapsepeti.order.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.kitapsepeti.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sipariş özeti ({@code GET /api/orders}). Para alanları detay uçla aynı (scale 2).
 * İç durumlar (stok durumu, ödeme id'si, late_payment_at) ve detaylar (adres, kalem listesi) bilerek yok.
 *
 * @param status {@code pending} | {@code paid} | {@code failed}
 * @param failureCode yalnızca {@code failed} siparişte dolu; diğerlerinde null
 * @param itemCount farklı kitap (satır) sayısı; adet toplamı değil
 */
@Schema(description = "Sipariş listesi öğesi (özet).")
public record OrderSummaryResponse(
		@Schema(requiredMode = REQUIRED, description = "Sipariş id'si.") UUID id,
		@Schema(requiredMode = REQUIRED, allowableValues = { "pending", "paid", "failed" }, description = "Sipariş durumu: pending, paid veya failed.") String status,
		@Schema(requiredMode = REQUIRED, types = { "string", "null" }, description = "Yalnızca failed siparişte dolu; diğerlerinde null.") String failureCode,
		@Schema(requiredMode = REQUIRED, description = "ISO 4217 para birimi.", example = "TRY") String currency,
		@Schema(requiredMode = REQUIRED, description = "Ödenecek toplam tutar, 2 ondalık basamak.") BigDecimal totalAmount,
		@Schema(requiredMode = REQUIRED, description = "farklı kitap (satır) sayısı; adet toplamı değil.") int itemCount,
		@Schema(requiredMode = REQUIRED, description = "Oluşturulma zamanı (UTC).") Instant createdAt,
		@Schema(requiredMode = REQUIRED, description = "Son değişiklik zamanı (UTC).") Instant updatedAt) {

	public OrderSummaryResponse {
		totalAmount = totalAmount != null ? totalAmount.setScale(2, RoundingMode.HALF_UP) : null;
	}

	/** JPQL constructor expression için (OrderStatus enum parametreli). */
	public OrderSummaryResponse(UUID id, OrderStatus status, String failureCode, String currency,
			BigDecimal totalAmount, Long itemCount, Instant createdAt, Instant updatedAt) {
		this(id, status != null ? status.dbValue() : null, failureCode, currency, totalAmount,
				itemCount != null ? itemCount.intValue() : 0, createdAt, updatedAt);
	}

	/** JPQL constructor expression için (String status parametreli alternatif). */
	public OrderSummaryResponse(UUID id, String status, String failureCode, String currency,
			BigDecimal totalAmount, Long itemCount, Instant createdAt, Instant updatedAt) {
		this(id, status, failureCode, currency, totalAmount,
				itemCount != null ? itemCount.intValue() : 0, createdAt, updatedAt);
	}

}
