package com.kitapsepeti.payment.entity;

import jakarta.persistence.Converter;

@Converter
public class PaymentStatusConverter extends StrictDbEnumConverter<PaymentStatus> {

	public PaymentStatusConverter() {
		super(PaymentStatus.class, "payment status");
	}

}
