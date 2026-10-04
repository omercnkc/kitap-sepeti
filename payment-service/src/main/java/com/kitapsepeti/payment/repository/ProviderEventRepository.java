package com.kitapsepeti.payment.repository;

import java.util.UUID;

import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.entity.ProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderEventRepository extends JpaRepository<ProviderEvent, UUID> {

	/** Olay daha önce işlendi mi ({@code uk_provider_events_provider_event}; olay kimliği harf duyarlı). */
	boolean existsByProviderTypeAndProviderEventId(PaymentProviderType providerType, String providerEventId);

}
