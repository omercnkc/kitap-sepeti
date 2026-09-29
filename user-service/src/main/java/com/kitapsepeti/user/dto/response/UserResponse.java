package com.kitapsepeti.user.dto.response;

import java.util.UUID;

import com.kitapsepeti.user.entity.Role;
import com.kitapsepeti.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Profil yanıtı. Parola hash'i bilinçli olarak yok. */
@Schema(description = "Kullanıcı profili.")
public record UserResponse(
		@Schema(description = "Kullanıcı id'si (UUIDv7).", example = "01a0ed1b-215d-7a27-9658-370bd146cb43")
		UUID id,
		@Schema(description = "E-posta (küçük harfe normalize).", example = "ali@example.com")
		String email,
		@Schema(example = "Ali")
		String firstName,
		@Schema(example = "Veli")
		String lastName,
		@Schema(description = "Telefon; yoksa null.", example = "5551112233")
		String phone,
		@Schema(description = "Rol.", example = "USER")
		Role role,
		@Schema(description = "Hesap durumu.", example = "ACTIVE")
		UserStatus status) {

}
