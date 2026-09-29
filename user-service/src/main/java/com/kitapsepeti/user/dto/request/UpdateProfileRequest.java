package com.kitapsepeti.user.dto.request;

import com.kitapsepeti.user.validation.NullOrNotBlank;
import jakarta.validation.constraints.Size;

/**
 * Kısmi profil güncellemesi: null alan değiştirilmez.
 * {@code phone == ""} telefonu siler; ad/soyad gönderildiyse boş olamaz.
 */
public record UpdateProfileRequest(
		@NullOrNotBlank @Size(min = 1, max = 80) String firstName,
		@NullOrNotBlank @Size(min = 1, max = 80) String lastName,
		@Size(max = 32) String phone) {

}
