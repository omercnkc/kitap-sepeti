package com.kitapsepeti.user.exception;

/** Kayıtta e-posta zaten kullanımda (409). */
public class EmailAlreadyExistsException extends ApiException {

	public EmailAlreadyExistsException() {
		super(ErrorCode.EMAIL_ALREADY_EXISTS);
	}

}
