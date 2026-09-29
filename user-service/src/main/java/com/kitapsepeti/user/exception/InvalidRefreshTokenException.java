package com.kitapsepeti.user.exception;

/** Refresh token bulunamadı, süresi doldu veya iptal edilmiş (401). */
public class InvalidRefreshTokenException extends ApiException {

	public InvalidRefreshTokenException() {
		super(ErrorCode.INVALID_REFRESH_TOKEN);
	}

}
