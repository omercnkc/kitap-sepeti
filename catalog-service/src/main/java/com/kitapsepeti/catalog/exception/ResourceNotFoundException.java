package com.kitapsepeti.catalog.exception;

/** İstenen kayıt yok (404). */
public class ResourceNotFoundException extends ApiException {

	public ResourceNotFoundException() {
		super(ErrorCode.RESOURCE_NOT_FOUND);
	}

	/** @param detail örn. "Book not found."; kayıt id'si veya kullanıcı verisi içermemeli */
	public ResourceNotFoundException(String detail) {
		super(ErrorCode.RESOURCE_NOT_FOUND, detail);
	}

}
