package com.kitapsepeti.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Giriş isteği. Parola politikası (uzunluk vb.) burada bilinçli olarak denetlenmez; aksi halde
 * hata yanıtı politikayı ele verir. Uymayan parola zaten eşleşmez ve 401 döner.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {

	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=***]";
	}

}
