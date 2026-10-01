package com.kitapsepeti.catalog.exception;

import com.kitapsepeti.common.error.ApiException;

/** Onaylanmış rezervasyon serbest bırakılamaz (409); iade akışı ayrıdır. */
public class ReservationCommittedException extends ApiException {

	public ReservationCommittedException() {
		super(CatalogErrorCode.RESERVATION_COMMITTED);
	}

}
