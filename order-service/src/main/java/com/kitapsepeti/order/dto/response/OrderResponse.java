package com.kitapsepeti.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.order.entity.Order;

/**
 * Sipariş. Para alanları JSON sayı, scale 2 (Cart API'siyle aynı). Stok durumu ve ödeme id'si bilerek yok (iç durum);
 * kullanıcı ve sepet id'si de yok.
 *
 * @param status {@code pending} | {@code paid} | {@code failed}
 * @param failureCode yalnızca {@code failed} siparişte dolu (ör. {@code OUT_OF_STOCK}); diğerlerinde null
 * @param items eklenme sırasıyla
 * @param address checkout anındaki teslimat adresinin kopyası
 */
public record OrderResponse(UUID id, String status, String failureCode, String currency, BigDecimal subtotal,
		BigDecimal discountAmount, BigDecimal totalAmount, List<OrderItemResponse> items, OrderAddressResponse address,
		Instant createdAt, Instant updatedAt) {

	public OrderResponse {
		items = List.copyOf(items);
	}

	/** Transaction içinde çağrılmalı: kalemler lazy yüklenir. */
	public static OrderResponse of(Order order) {
		return new OrderResponse(order.getId(), order.getStatus().dbValue(), order.getFailureCode(),
				order.getCurrency(), order.getSubtotal(), order.getDiscountAmount(), order.getTotalAmount(),
				order.getItems().stream().map(OrderItemResponse::of).toList(),
				OrderAddressResponse.of(order.getAddressSnapshot()), order.getCreatedAt(), order.getUpdatedAt());
	}

	@Override
	public String toString() {
		return "OrderResponse[status=" + this.status + ", items=" + this.items.size() + "]";
	}

}
