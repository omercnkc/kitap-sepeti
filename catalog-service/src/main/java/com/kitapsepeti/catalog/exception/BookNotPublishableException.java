package com.kitapsepeti.catalog.exception;

import java.util.List;

/**
 * Kitap yayın koşullarını sağlamıyor (409). Detail hangi koşulların eksik olduğunu söyler; kitabın
 * değerlerini (fiyat, başlık...) içermez.
 */
public class BookNotPublishableException extends ApiException {

	public BookNotPublishableException(List<String> missingRequirements) {
		super(ErrorCode.BOOK_NOT_PUBLISHABLE, ErrorCode.BOOK_NOT_PUBLISHABLE.defaultDetail() + " Missing: "
				+ String.join(", ", missingRequirements) + ".");
	}

}
