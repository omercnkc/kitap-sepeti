package com.kitapsepeti.user.dto.request;

import com.kitapsepeti.user.validation.NullOrNotBlank;
import com.kitapsepeti.user.validation.TrMobilePhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Kısmi adres güncellemesi: null alan değiştirilmez. Zorunlu alanlar gönderildiyse boş olamaz;
 * opsiyonel alanlara (label, line2, district, postalCode) boş metin gönderilirse alan silinir.
 * {@code isDefault=false} şu anki varsayılan adreste 409 döner (önce başka adres varsayılan yapılmalı).
 */
@Schema(description = "Kısmi adres güncellemesi. null/gönderilmemiş = değiştirme; opsiyonel alanda \"\" = sil; "
		+ "zorunlu alan boş olamaz. Zorunlu: recipientName, phone, line1, city. "
		+ "Opsiyonel: label, line2, district, postalCode.")
public record UpdateAddressRequest(
		@Schema(description = "Etiket; \"\" gönderilirse silinir.", example = "İş")
		@Size(max = 40) String label,
		@Schema(description = "Alıcının adı soyadı; gönderildiyse boş olamaz.", example = "Ali Veli")
		@NullOrNotBlank @Size(max = 120) String recipientName,
		@Schema(description = "Alıcının TR cep telefonu; gönderildiyse boş olamaz. Kabul: 05… / 5… / +905…; "
				+ "saklanan format 5xxxxxxxxx.", example = "5551112233")
		@NullOrNotBlank @Size(max = 32) @TrMobilePhone String phone,
		@Schema(description = "Adres satırı 1; gönderildiyse boş olamaz.", example = "Büyükdere Cad. No:100")
		@NullOrNotBlank @Size(max = 200) String line1,
		@Schema(description = "Adres satırı 2; \"\" gönderilirse silinir.", example = "")
		@Size(max = 200) String line2,
		@Schema(description = "İlçe; \"\" gönderilirse silinir.", example = "Şişli")
		@Size(max = 80) String district,
		@Schema(description = "İl; gönderildiyse boş olamaz.", example = "İstanbul")
		@NullOrNotBlank @Size(max = 80) String city,
		@Schema(description = "Posta kodu; \"\" gönderilirse silinir.", example = "34394")
		@Size(max = 16) String postalCode,
		@Schema(description = "ISO 3166-1 alpha-2, büyük harf.", example = "TR")
		@Pattern(regexp = "^[A-Z]{2}$") String country,
		@Schema(description = "`true`: bu adresi varsayılan yap. Varsayılan adrese `false` gönderilirse 409 "
				+ "`DEFAULT_ADDRESS_REQUIRED`.", example = "true")
		Boolean isDefault) {

}
