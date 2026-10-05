package com.kitapsepeti.order.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.order.entity.Order;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sipariş. Para alanları JSON sayı, scale 2 (Cart API'siyle aynı). Stok durumu ve ödeme id'si bilerek yok (iç durum);
 * kullanıcı ve sepet id'si de yok.
 *
 * @param status {@code pending} | {@code paid} | {@code failed}
 * @param failureCode yalnızca {@code failed} siparişte dolu (ör. {@code OUT_OF_STOCK}); diğerlerinde null
 * @param items eklenme sırasıyla
 * @param address checkout anındaki teslimat adresinin kopyası
 */
@Schema(description = "Sipariş. Tüm alanlar her yanıtta bulunur; boş olanlar null gelir.")
public record OrderResponse(
		@Schema(requiredMode = REQUIRED, description = "Sipariş id'si.") UUID id,
		@Schema(requiredMode = REQUIRED, allowableValues = { "pending", "paid", "failed" }, description = "Sipariş durumu: pending, paid veya failed.") String status,
		@Schema(requiredMode = REQUIRED, types = { "string", "null" }, description = "Yalnızca failed siparişte dolu (ör. OUT_OF_STOCK); diğerlerinde null.") String failureCode,
		@Schema(requiredMode = REQUIRED, description = "ISO 4217 para birimi.", example = "TRY") String currency,
		@Schema(requiredMode = REQUIRED, description = "Kalemlerin toplamı, 2 ondalık basamak.") BigDecimal subtotal,
		@Schema(requiredMode = REQUIRED, description = "İndirim tutarı, 2 ondalık basamak.") BigDecimal discountAmount,
		@Schema(requiredMode = REQUIRED, description = "Ödenecek toplam tutar, 2 ondalık basamak.") BigDecimal totalAmount,
		@Schema(requiredMode = REQUIRED, description = "Sipariş kalemleri, eklenme sırasıyla.") List<OrderItemResponse> items,
		@Schema(requiredMode = REQUIRED, description = "Checkout anındaki teslimat adresinin kopyası.") OrderAddressResponse address,
		@Schema(requiredMode = REQUIRED, description = "Oluşturulma zamanı (UTC).") Instant createdAt,
		@Schema(requiredMode = REQUIRED, description = "Son değişiklik zamanı (UTC).") Instant updatedAt) {

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
