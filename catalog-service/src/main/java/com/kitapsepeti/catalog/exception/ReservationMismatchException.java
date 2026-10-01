package com.kitapsepeti.catalog.exception;

/** Siparişin mevcut rezervasyonu istenen (kitap, adet) kümesinden farklı (409). */
public class ReservationMismatchException extends ApiException {

	public ReservationMismatchException() {
		super(ErrorCode.RESERVATION_MISMATCH);
	}

}
