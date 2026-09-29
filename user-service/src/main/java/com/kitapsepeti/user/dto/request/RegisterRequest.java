package com.kitapsepeti.user.dto.request;

import com.kitapsepeti.user.validation.Utf8MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Kayıt isteği. {@code phone} opsiyonel. */
public record RegisterRequest(
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 8, max = 72) @Utf8MaxBytes(72) String password,
		@NotBlank @Size(max = 80) String firstName,
		@NotBlank @Size(max = 80) String lastName,
		@Size(max = 32) String phone) {

	/** Parola loglara veya hata mesajlarına düşmesin. */
	@Override
	public String toString() {
		return "RegisterRequest[email=" + email + ", password=***, firstName=" + firstName + ", lastName="
				+ lastName + ", phone=" + phone + "]";
	}

}
