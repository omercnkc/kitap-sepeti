package com.kitapsepeti.catalog.exception;

/** Slug aynı türde başka bir kayıtta kullanılıyor (409). Yarışta aynı kodu DB kısıtı ({@link DbConstraints}) verir. */
public class SlugAlreadyExistsException extends ApiException {

	public SlugAlreadyExistsException() {
		super(ErrorCode.SLUG_ALREADY_EXISTS);
	}

}
