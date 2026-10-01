package com.kitapsepeti.user.exception;

import com.kitapsepeti.common.error.ApiException;

/** Kayıtta e-posta zaten kullanımda (409). */
public class EmailAlreadyExistsException extends ApiException {

	public EmailAlreadyExistsException() {
		super(UserErrorCode.EMAIL_ALREADY_EXISTS);
	}

}
