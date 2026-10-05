package com.kitapsepeti.order.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.util.UUID;

import com.kitapsepeti.order.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sipariş kalemi: checkout anındaki başlık ve Catalog fiyatı, sepetteki adet.
 *
 * @param unitPrice scale 2
 * @param lineTotal {@code unitPrice × quantity}, scale 2
 */
@Schema(description = "Sipariş kalemi.")
public record OrderItemResponse(
		@Schema(requiredMode = REQUIRED, description = "Kitap id'si.") UUID bookId,
		@Schema(requiredMode = REQUIRED, description = "Checkout anındaki kitap başlığı.") String title,
		@Schema(requiredMode = REQUIRED, description = "Sipariş edilen adet.", minimum = "1") int quantity,
		@Schema(requiredMode = REQUIRED, description = "Checkout anındaki birim fiyat, 2 ondalık basamak.") BigDecimal unitPrice,
		@Schema(requiredMode = REQUIRED, description = "Satır toplam tutarı (quantity × unitPrice), 2 ondalık basamak.") BigDecimal lineTotal) {

	static OrderItemResponse of(OrderItem item) {
		return new OrderItemResponse(item.getBookId(), item.getTitleSnapshot(), item.getQuantity(), item.getUnitPrice(),
				item.getLineTotal());
	}

	@Override
	public String toString() {
		return "OrderItemResponse[redacted]";
	}

}
