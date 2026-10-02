package com.kitapsepeti.cart.exception;

import com.kitapsepeti.common.error.ApiException;

/** Kitap katalogda yok ya da satışta değil (409). Yanıt kitap id'si taşımaz; istemci hangi kitabı eklediğini bilir. */
public class BookNotAvailableException extends ApiException {

	public BookNotAvailableException() {
		super(CartErrorCode.BOOK_NOT_AVAILABLE);
	}

}
