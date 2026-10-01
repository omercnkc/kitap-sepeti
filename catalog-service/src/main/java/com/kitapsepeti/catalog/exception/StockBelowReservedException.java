package com.kitapsepeti.catalog.exception;

import com.kitapsepeti.common.error.ApiException;

/** Stok düzeltmesi sonrası stok, rezerve miktarın altına inerdi (409). */
public class StockBelowReservedException extends ApiException {

	public StockBelowReservedException() {
		super(CatalogErrorCode.STOCK_BELOW_RESERVED);
	}

}
