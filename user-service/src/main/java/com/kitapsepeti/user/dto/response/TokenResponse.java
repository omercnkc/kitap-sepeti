package com.kitapsepeti.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kayıt, giriş ve refresh yanıtı.
 * @param expiresIn access token'ın kalan ömrü, saniye
 */
@Schema(description = "Token çifti. Yanıt `Cache-Control: no-store` ile döner.")
public record TokenResponse(
		@Schema(description = "RS256 imzalı JWT; `Authorization: Bearer <accessToken>` ile gönderilir.",
				example = "eyJraWQiOiJoTjN4Li4uIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIuLi4ifQ.c2lnbmF0dXJl")
		String accessToken,
		@Schema(description = "Opak refresh token; yalnızca `POST /api/auth/refresh`'te ve bir kez kullanılır.",
				example = "fIC1kvizEe7Qm3p0Yx9aLr2Nw8cVtKjHbUoS4dGyZ6E")
		String refreshToken,
		@Schema(description = "Her zaman `Bearer`.", example = "Bearer")
		String tokenType,
		@Schema(description = "Access token'ın ömrü, saniye.", example = "900")
		long expiresIn) {

	public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn) {
		return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
	}

	@Override
	public String toString() {
		return "TokenResponse[accessToken=***, refreshToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn
				+ "]";
	}

}
