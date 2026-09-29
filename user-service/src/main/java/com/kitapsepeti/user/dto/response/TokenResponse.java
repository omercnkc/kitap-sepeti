package com.kitapsepeti.user.dto.response;

/**
 * Kayıt, giriş ve refresh yanıtı.
 * @param expiresIn access token'ın kalan ömrü, saniye
 */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

	public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn) {
		return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
	}

	@Override
	public String toString() {
		return "TokenResponse[accessToken=***, refreshToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn
				+ "]";
	}

}
