package com.kitapsepeti.user.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Adres.")
public record AddressResponse(
		@Schema(description = "Adres id'si.", example = "01a0ed1b-22f7-7a76-aa96-c8ec0f3b3a4a")
		UUID id,
		@Schema(description = "Etiket; yoksa null.", example = "Ev")
		String label,
		@Schema(example = "Ali Veli")
		String recipientName,
		@Schema(example = "5551112233")
		String phone,
		@Schema(example = "Atatürk Cad. No:1")
		String line1,
		@Schema(description = "Yoksa null.", example = "Daire 5")
		String line2,
		@Schema(description = "Yoksa null.", example = "Kadıköy")
		String district,
		@Schema(example = "İstanbul")
		String city,
		@Schema(description = "Yoksa null.", example = "34710")
		String postalCode,
		@Schema(description = "ISO 3166-1 alpha-2.", example = "TR")
		String country,
		@Schema(description = "Varsayılan adres mi.", example = "true")
		boolean isDefault,
		@Schema(description = "Oluşturulma zamanı (UTC).", example = "2026-09-29T12:19:33.752772Z")
		Instant createdAt) {

}
