package com.kitapsepeti.user.exception;

/**
 * Girişte e-posta veya parola hatalı (401). Hangisinin yanlış olduğu bilinçli olarak söylenmez;
 * aksi halde kayıtlı e-postalar tespit edilebilir.
 */
public class InvalidCredentialsException extends ApiException {

	public InvalidCredentialsException() {
		super(ErrorCode.INVALID_CREDENTIALS);
	}

}
