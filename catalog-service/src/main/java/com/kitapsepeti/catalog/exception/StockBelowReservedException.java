package com.kitapsepeti.catalog.exception;

/** Stok düzeltmesi sonrası stok, rezerve miktarın altına inerdi (409). */
public class StockBelowReservedException extends ApiException {

	public StockBelowReservedException() {
		super(ErrorCode.STOCK_BELOW_RESERVED);
	}

}
