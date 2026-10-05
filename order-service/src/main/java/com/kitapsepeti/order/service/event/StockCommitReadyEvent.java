package com.kitapsepeti.order.service.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Bir siparişin ödemesi başarıyla tamamlandığında (transaction commit sonrasında)
 * Catalog stok kesinleştirmesinin (commit) tetiklenmesi için yayınlanan iç uygulama olayı.
 */
public record StockCommitReadyEvent(UUID orderId) {

	public StockCommitReadyEvent {
		Objects.requireNonNull(orderId, "orderId");
	}

}
