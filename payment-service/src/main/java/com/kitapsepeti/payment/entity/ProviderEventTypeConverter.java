package com.kitapsepeti.payment.entity;

import jakarta.persistence.Converter;

@Converter
public class ProviderEventTypeConverter extends StrictDbEnumConverter<ProviderEventType> {

	public ProviderEventTypeConverter() {
		super(ProviderEventType.class, "provider event type");
	}

}
