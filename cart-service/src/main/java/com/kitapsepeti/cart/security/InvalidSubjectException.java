package com.kitapsepeti.cart.security;

import com.kitapsepeti.common.error.ApiException;
import com.kitapsepeti.common.error.CommonErrorCode;

/** Kimlik JWT değil ya da {@code sub} kullanıcı UUID'si değil: 401 + {@code Bearer error="invalid_token"}. */
public class InvalidSubjectException extends ApiException {

	public InvalidSubjectException() {
		super(CommonErrorCode.UNAUTHORIZED);
	}

}
