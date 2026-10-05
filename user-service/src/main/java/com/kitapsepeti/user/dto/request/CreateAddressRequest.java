package com.kitapsepeti.user.dto.request;

import com.kitapsepeti.user.validation.TrMobilePhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yeni adres. {@code country} ISO 3166-1 alpha-2 (büyük harf), null ise "TR".
 * {@code isDefault} null/false olsa da kullanıcının ilk adresi varsayılan olur.
 */
@Schema(description = "Yeni adres.")
public record CreateAddressRequest(
		@Schema(description = "Kısa etiket (opsiyonel).", example = "Ev")
		@Size(max = 40) String label,
		@Schema(description = "Alıcının adı soyadı.", example = "Ali Veli")
		@NotBlank @Size(max = 120) String recipientName,
		@Schema(description = "Alıcının TR cep telefonu. Kabul: 05… / 5… / +905…; saklanan format 5xxxxxxxxx.",
				example = "5551112233")
		@NotBlank @Size(max = 32) @TrMobilePhone String phone,
		@Schema(description = "Adres satırı 1.", example = "Atatürk Cad. No:1")
		@NotBlank @Size(max = 200) String line1,
		@Schema(description = "Adres satırı 2 (opsiyonel).", example = "Daire 5")
		@Size(max = 200) String line2,
		@Schema(description = "İlçe (opsiyonel).", example = "Kadıköy")
		@Size(max = 80) String district,
		@Schema(description = "İl.", example = "İstanbul")
		@NotBlank @Size(max = 80) String city,
		@Schema(description = "Posta kodu (opsiyonel).", example = "34710")
		@Size(max = 16) String postalCode,
		@Schema(description = "ISO 3166-1 alpha-2, büyük harf; gönderilmezse `TR`.", example = "TR")
		@Pattern(regexp = "^[A-Z]{2}$") String country,
		@Schema(description = "Varsayılan adres yap. Kullanıcının ilk adresi her durumda varsayılan olur.",
				example = "false")
		Boolean isDefault) {

}
