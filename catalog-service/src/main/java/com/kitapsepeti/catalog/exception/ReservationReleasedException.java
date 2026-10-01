package com.kitapsepeti.catalog.exception;

import com.kitapsepeti.common.error.ApiException;

/** Serbest bırakılmış rezervasyon onaylanamaz (409). */
public class ReservationReleasedException extends ApiException {

	public ReservationReleasedException() {
		super(CatalogErrorCode.RESERVATION_RELEASED);
	}

}
