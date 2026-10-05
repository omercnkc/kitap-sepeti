package com.kitapsepeti.order.service.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Bir siparişin ödemesi başarısız olduğunda (transaction commit sonrasında)
 * Catalog stok rezervasyonunun serbest bırakılmasının (release) tetiklenmesi için yayınlanan iç uygulama olayı.
 */
public record StockReleaseReadyEvent(UUID orderId) {

	public StockReleaseReadyEvent {
		Objects.requireNonNull(orderId, "orderId");
	}

}
