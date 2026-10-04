package com.kitapsepeti.order.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.kitapsepeti.order.entity.OrderItem;

/**
 * Sipariş kalemi: checkout anındaki başlık ve Catalog fiyatı, sepetteki adet.
 *
 * @param unitPrice scale 2
 * @param lineTotal {@code unitPrice × quantity}, scale 2
 */
public record OrderItemResponse(UUID bookId, String title, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

	static OrderItemResponse of(OrderItem item) {
		return new OrderItemResponse(item.getBookId(), item.getTitleSnapshot(), item.getQuantity(), item.getUnitPrice(),
				item.getLineTotal());
	}

	@Override
	public String toString() {
		return "OrderItemResponse[redacted]";
	}

}
