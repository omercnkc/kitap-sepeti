package com.kitapsepeti.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Checkout gövdesi: yalnızca teslimat adresi. Sepet, fiyat ve kullanıcı istekten alınmaz (Cart, Catalog ve token'dan).
 * Bilinmeyen alanlar yok sayılır (diğer servislerle aynı Jackson politikası).
 */
public record CheckoutRequest(@NotNull @Valid AddressRequest address) {

	@Override
	public String toString() {
		return "CheckoutRequest[redacted]";
	}

}
