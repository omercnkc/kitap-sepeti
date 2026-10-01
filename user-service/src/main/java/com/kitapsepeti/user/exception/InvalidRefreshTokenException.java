package com.kitapsepeti.user.exception;

import com.kitapsepeti.common.error.ApiException;

/** Refresh token bulunamadı, süresi doldu veya iptal edilmiş (401). */
public class InvalidRefreshTokenException extends ApiException {

	public InvalidRefreshTokenException() {
		super(UserErrorCode.INVALID_REFRESH_TOKEN);
	}

}
