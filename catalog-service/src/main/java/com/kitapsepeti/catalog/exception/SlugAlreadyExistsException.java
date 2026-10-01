package com.kitapsepeti.catalog.exception;

import com.kitapsepeti.common.error.ApiException;

/** Slug aynı türde başka bir kayıtta kullanılıyor (409). Yarışta aynı kodu DB kısıtı ({@link DbConstraintCodes}) verir. */
public class SlugAlreadyExistsException extends ApiException {

	public SlugAlreadyExistsException() {
		super(CatalogErrorCode.SLUG_ALREADY_EXISTS);
	}

}
