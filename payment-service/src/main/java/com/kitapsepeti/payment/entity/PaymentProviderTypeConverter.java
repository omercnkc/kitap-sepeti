package com.kitapsepeti.payment.entity;

import jakarta.persistence.Converter;

@Converter
public class PaymentProviderTypeConverter extends StrictDbEnumConverter<PaymentProviderType> {

	public PaymentProviderTypeConverter() {
		super(PaymentProviderType.class, "payment provider");
	}

}
