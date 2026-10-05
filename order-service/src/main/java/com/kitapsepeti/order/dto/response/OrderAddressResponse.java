package com.kitapsepeti.order.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.kitapsepeti.order.entity.AddressSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Siparişin teslimat adresi (checkout anındaki kopya); opsiyonel alanlar boşsa null. {@link #toString()} alan değeri
 * içermez (kişisel veri).
 */
@Schema(description = "Siparişin teslimat adresi (checkout anındaki anlık kopya).")
public record OrderAddressResponse(
		@Schema(requiredMode = REQUIRED, description = "Teslim alacak kişinin adı soyadı.") String recipientName,
		@Schema(requiredMode = REQUIRED, description = "Telefon numarası.") String phone,
		@Schema(requiredMode = REQUIRED, description = "Adres satırı 1.") String line1,
		@Schema(requiredMode = REQUIRED, types = { "string", "null" }, description = "Adres satırı 2; yoksa null.") String line2,
		@Schema(requiredMode = REQUIRED, types = { "string", "null" }, description = "İlçe; yoksa null.") String district,
		@Schema(requiredMode = REQUIRED, description = "İl.") String city,
		@Schema(requiredMode = REQUIRED, types = { "string", "null" }, description = "Posta kodu; yoksa null.") String postalCode,
		@Schema(requiredMode = REQUIRED, description = "ISO 3166-1 alpha-2 ülke kodu.", example = "TR") String country) {

	static OrderAddressResponse of(AddressSnapshot address) {
		return new OrderAddressResponse(address.recipientName(), address.phone(), address.line1(), address.line2(),
				address.district(), address.city(), address.postalCode(), address.country());
	}

	@Override
	public String toString() {
		return "OrderAddressResponse[redacted]";
	}

}
