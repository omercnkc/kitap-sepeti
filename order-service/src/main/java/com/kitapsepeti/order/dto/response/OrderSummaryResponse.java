package com.kitapsepeti.order.dto.response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.kitapsepeti.order.entity.OrderStatus;

/**
 * Sipariş özeti ({@code GET /api/orders}). Para alanları detay uçla aynı (scale 2).
 * İç durumlar (stok durumu, ödeme id'si, late_payment_at) ve detaylar (adres, kalem listesi) bilerek yok.
 *
 * @param status {@code pending} | {@code paid} | {@code failed}
 * @param failureCode yalnızca {@code failed} siparişte dolu; diğerlerinde null
 * @param itemCount siparişteki kalem satırı sayısı
 */
public record OrderSummaryResponse(
		UUID id,
		String status,
		String failureCode,
		String currency,
		BigDecimal totalAmount,
		int itemCount,
		Instant createdAt,
		Instant updatedAt) {

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
