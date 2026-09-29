package com.kitapsepeti.user.dto.request;

import com.kitapsepeti.user.validation.Utf8MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Kayıt isteği. {@code phone} opsiyonel. */
@Schema(description = "Kayıt isteği.")
public record RegisterRequest(
		@Schema(description = "E-posta; büyük/küçük harf duyarsız ve benzersiz.", example = "ali@example.com")
		@NotBlank @Email @Size(max = 255) String email,
		@Schema(description = "Parola: 8–72 karakter ve en fazla 72 byte (UTF-8).", example = "Gizli-Parola-7391",
				accessMode = Schema.AccessMode.WRITE_ONLY)
		@NotBlank @Size(min = 8, max = 72) @Utf8MaxBytes(72) String password,
		@Schema(description = "Ad.", example = "Ali")
		@NotBlank @Size(max = 80) String firstName,
		@Schema(description = "Soyad.", example = "Veli")
		@NotBlank @Size(max = 80) String lastName,
		@Schema(description = "Telefon (opsiyonel).", example = "5551112233")
		@Size(max = 32) String phone) {

	/** Parola loglara veya hata mesajlarına düşmesin. */
	@Override
	public String toString() {
		return "RegisterRequest[email=" + email + ", password=***, firstName=" + firstName + ", lastName="
				+ lastName + ", phone=" + phone + "]";
	}

}
