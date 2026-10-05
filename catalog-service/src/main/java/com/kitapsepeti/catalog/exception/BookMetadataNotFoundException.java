package com.kitapsepeti.catalog.exception;

import com.kitapsepeti.common.error.ApiException;

/** Open Library (veya diğer sağlayıcı) bu ISBN için metadata döndürmedi (404). */
public class BookMetadataNotFoundException extends ApiException {

	public BookMetadataNotFoundException() {
		super(CatalogErrorCode.BOOK_METADATA_NOT_FOUND);
	}

}
