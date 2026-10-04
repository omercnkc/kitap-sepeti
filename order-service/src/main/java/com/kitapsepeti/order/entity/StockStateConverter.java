package com.kitapsepeti.order.entity;

import jakarta.persistence.Converter;

@Converter
public class StockStateConverter extends StrictDbEnumConverter<StockState> {

	public StockStateConverter() {
		super(StockState.class, "stock state");
	}

}
