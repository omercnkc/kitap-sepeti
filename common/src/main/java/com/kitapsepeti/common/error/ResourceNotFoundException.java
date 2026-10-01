package com.kitapsepeti.common.error;

/** İstenen kayıt yok ya da isteyen kullanıcıya ait değil (404). */
public class ResourceNotFoundException extends ApiException {

	public ResourceNotFoundException() {
		super(CommonErrorCode.RESOURCE_NOT_FOUND);
	}

	/** @param detail örn. "Book not found."; kayıt id'si veya kullanıcı verisi içermemeli */
	public ResourceNotFoundException(String detail) {
		super(CommonErrorCode.RESOURCE_NOT_FOUND, detail);
	}

}
