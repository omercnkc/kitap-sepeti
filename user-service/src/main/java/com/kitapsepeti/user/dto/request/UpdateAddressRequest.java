package com.kitapsepeti.user.dto.request;

import com.kitapsepeti.user.validation.NullOrNotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Kısmi adres güncellemesi: null alan değiştirilmez. Zorunlu alanlar gönderildiyse boş olamaz;
 * opsiyonel alanlara (label, line2, district, postalCode) boş metin gönderilirse alan silinir.
 * {@code isDefault=false} şu anki varsayılan adreste 409 döner (önce başka adres varsayılan yapılmalı).
 */
public record UpdateAddressRequest(
		@Size(max = 40) String label,
		@NullOrNotBlank @Size(max = 120) String recipientName,
		@NullOrNotBlank @Size(max = 32) String phone,
		@NullOrNotBlank @Size(max = 200) String line1,
		@Size(max = 200) String line2,
		@Size(max = 80) String district,
		@NullOrNotBlank @Size(max = 80) String city,
		@Size(max = 16) String postalCode,
		@Pattern(regexp = "^[A-Z]{2}$") String country,
		Boolean isDefault) {

}
