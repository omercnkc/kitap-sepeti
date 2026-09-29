package com.kitapsepeti.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yeni adres. {@code country} ISO 3166-1 alpha-2 (büyük harf), null ise "TR".
 * {@code isDefault} null/false olsa da kullanıcının ilk adresi varsayılan olur.
 */
public record CreateAddressRequest(
		@Size(max = 40) String label,
		@NotBlank @Size(max = 120) String recipientName,
		@NotBlank @Size(max = 32) String phone,
		@NotBlank @Size(max = 200) String line1,
		@Size(max = 200) String line2,
		@Size(max = 80) String district,
		@NotBlank @Size(max = 80) String city,
		@Size(max = 16) String postalCode,
		@Pattern(regexp = "^[A-Z]{2}$") String country,
		Boolean isDefault) {

}
