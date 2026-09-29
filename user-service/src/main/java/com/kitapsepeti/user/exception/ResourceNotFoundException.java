package com.kitapsepeti.user.exception;

/** İstenen kayıt yok ya da isteyen kullanıcıya ait değil (404). */
public class ResourceNotFoundException extends ApiException {

	public ResourceNotFoundException() {
		super(ErrorCode.RESOURCE_NOT_FOUND);
	}

	/** @param detail örn. "Address not found."; kayıt id'si veya kullanıcı verisi içermemeli */
	public ResourceNotFoundException(String detail) {
		super(ErrorCode.RESOURCE_NOT_FOUND, detail);
	}

}
