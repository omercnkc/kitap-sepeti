package com.kitapsepeti.catalog.exception;

import java.util.List;

import com.kitapsepeti.common.error.ApiException;

/**
 * Kitap yayın koşullarını sağlamıyor (409). Detail hangi koşulların eksik olduğunu söyler; kitabın
 * değerlerini (fiyat, başlık...) içermez.
 */
public class BookNotPublishableException extends ApiException {

	public BookNotPublishableException(List<String> missingRequirements) {
		super(CatalogErrorCode.BOOK_NOT_PUBLISHABLE, CatalogErrorCode.BOOK_NOT_PUBLISHABLE.defaultDetail() + " Missing: "
				+ String.join(", ", missingRequirements) + ".");
	}

}
