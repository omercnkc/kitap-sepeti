package com.kitapsepeti.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Giriş isteği. Parola politikası (uzunluk vb.) burada bilinçli olarak denetlenmez; aksi halde
 * hata yanıtı politikayı ele verir. Uymayan parola zaten eşleşmez ve 401 döner.
 */
@Schema(description = "Giriş isteği.")
public record LoginRequest(
		@Schema(description = "Kayıtlı e-posta.", example = "ali@example.com")
		@NotBlank String email,
		@Schema(description = "Parola.", example = "Gizli-Parola-7391", accessMode = Schema.AccessMode.WRITE_ONLY)
		@NotBlank String password) {

	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=***]";
	}

}
