package com.kitapsepeti.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Token yenileme isteği.")
public record RefreshRequest(
		@Schema(description = "Son alınan refresh token (tek kullanımlık).",
				example = "fIC1kvizEe7Qm3p0Yx9aLr2Nw8cVtKjHbUoS4dGyZ6E")
		@NotBlank String refreshToken) {

	@Override
	public String toString() {
		return "RefreshRequest[refreshToken=***]";
	}

}
