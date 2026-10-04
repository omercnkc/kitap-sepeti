package com.kitapsepeti.order.dto.request;

import com.kitapsepeti.order.entity.AddressSnapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Teslimat adresi. Kurallar user-service {@code CreateAddressRequest} ile aynı (zorunluluk, uzunluk, ülke biçimi;
 * telefonda yalnızca uzunluk, biçim kuralı yok); tek fark {@code country} burada zorunlu (user-service'te null → "TR").
 * Opsiyonel alanlarda boş metin null olarak saklanır (user-service {@code AddressMapper} ile aynı).
 * {@link #toString()} alan değeri içermez (kişisel veri).
 */
public record AddressRequest(
		@NotBlank @Size(max = 120) String recipientName,
		@NotBlank @Size(max = 32) String phone,
		@NotBlank @Size(max = 200) String line1,
		@Size(max = 200) String line2,
		@Size(max = 80) String district,
		@NotBlank @Size(max = 80) String city,
		@Size(max = 16) String postalCode,
		@NotNull @Pattern(regexp = "^[A-Z]{2}$") String country) {

	public AddressSnapshot toSnapshot() {
		return new AddressSnapshot(this.recipientName, this.phone, this.line1, blankToNull(this.line2),
				blankToNull(this.district), this.city, blankToNull(this.postalCode), this.country);
	}

	@Override
	public String toString() {
		return "AddressRequest[redacted]";
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

}
