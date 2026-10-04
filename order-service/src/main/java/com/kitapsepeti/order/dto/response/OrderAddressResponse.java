package com.kitapsepeti.order.dto.response;

import com.kitapsepeti.order.entity.AddressSnapshot;

/**
 * Siparişin teslimat adresi (checkout anındaki kopya); opsiyonel alanlar boşsa null. {@link #toString()} alan değeri
 * içermez (kişisel veri).
 */
public record OrderAddressResponse(String recipientName, String phone, String line1, String line2, String district,
		String city, String postalCode, String country) {

	static OrderAddressResponse of(AddressSnapshot address) {
		return new OrderAddressResponse(address.recipientName(), address.phone(), address.line1(), address.line2(),
				address.district(), address.city(), address.postalCode(), address.country());
	}

	@Override
	public String toString() {
		return "OrderAddressResponse[redacted]";
	}

}
