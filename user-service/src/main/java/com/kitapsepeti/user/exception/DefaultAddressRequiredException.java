package com.kitapsepeti.user.exception;

import com.kitapsepeti.common.error.ApiException;

/** Varsayılan adres, yerine başka bir varsayılan seçilmeden varsayılanlıktan çıkarılamaz (409). */
public class DefaultAddressRequiredException extends ApiException {

	public DefaultAddressRequiredException() {
		super(UserErrorCode.DEFAULT_ADDRESS_REQUIRED);
	}

}
