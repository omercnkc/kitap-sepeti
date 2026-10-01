package com.kitapsepeti.catalog.exception;

import com.kitapsepeti.common.error.ApiException;

/** Siparişin mevcut rezervasyonu istenen (kitap, adet) kümesinden farklı (409). */
public class ReservationMismatchException extends ApiException {

	public ReservationMismatchException() {
		super(CatalogErrorCode.RESERVATION_MISMATCH);
	}

}
