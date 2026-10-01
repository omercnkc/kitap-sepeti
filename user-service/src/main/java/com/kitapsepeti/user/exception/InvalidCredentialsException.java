package com.kitapsepeti.user.exception;

import com.kitapsepeti.common.error.ApiException;

/**
 * Girişte e-posta veya parola hatalı (401). Hangisinin yanlış olduğu bilinçli olarak söylenmez;
 * aksi halde kayıtlı e-postalar tespit edilebilir.
 */
public class InvalidCredentialsException extends ApiException {

	public InvalidCredentialsException() {
		super(UserErrorCode.INVALID_CREDENTIALS);
	}

}
