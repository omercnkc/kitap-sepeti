package com.kitapsepeti.catalog.exception;

/** Serbest bırakılmış rezervasyon onaylanamaz (409). */
public class ReservationReleasedException extends ApiException {

	public ReservationReleasedException() {
		super(ErrorCode.RESERVATION_RELEASED);
	}

}
