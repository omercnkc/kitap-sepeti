package com.kitapsepeti.user.exception;

/** Varsayılan adres, yerine başka bir varsayılan seçilmeden varsayılanlıktan çıkarılamaz (409). */
public class DefaultAddressRequiredException extends ApiException {

	public DefaultAddressRequiredException() {
		super(ErrorCode.DEFAULT_ADDRESS_REQUIRED);
	}

}
