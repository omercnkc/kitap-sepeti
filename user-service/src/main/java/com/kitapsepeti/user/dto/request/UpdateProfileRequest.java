package com.kitapsepeti.user.dto.request;

import com.kitapsepeti.user.validation.NullOrNotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Kısmi profil güncellemesi: null alan değiştirilmez.
 * {@code phone == ""} telefonu siler; ad/soyad gönderildiyse boş olamaz.
 */
@Schema(description = "Kısmi profil güncellemesi. null/gönderilmemiş = değiştirme; opsiyonel alanda \"\" = sil; "
		+ "zorunlu alan boş olamaz. Zorunlu: firstName, lastName. Opsiyonel: phone.")
public record UpdateProfileRequest(
		@Schema(description = "Ad; gönderildiyse boş olamaz.", example = "Ayşe")
		@NullOrNotBlank @Size(min = 1, max = 80) String firstName,
		@Schema(description = "Soyad; gönderildiyse boş olamaz.", example = "Yılmaz")
		@NullOrNotBlank @Size(min = 1, max = 80) String lastName,
		@Schema(description = "Telefon; \"\" gönderilirse silinir.", example = "5559998877")
		@Size(max = 32) String phone) {

}
