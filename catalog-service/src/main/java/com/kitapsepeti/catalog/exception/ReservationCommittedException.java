package com.kitapsepeti.catalog.exception;

/** Onaylanmış rezervasyon serbest bırakılamaz (409); iade akışı ayrıdır. */
public class ReservationCommittedException extends ApiException {

	public ReservationCommittedException() {
		super(ErrorCode.RESERVATION_COMMITTED);
	}

}
