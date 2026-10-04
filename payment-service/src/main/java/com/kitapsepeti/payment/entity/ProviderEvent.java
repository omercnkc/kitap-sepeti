package com.kitapsepeti.payment.entity;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**
 * İşlenmiş sağlayıcı olayı ({@code provider_events}); aynı olay ikinci kez kaydedilemez
 * ({@code uk_provider_events_provider_event}). Değişmez: yalnızca {@link #record} ile oluşur, Hibernate UPDATE yazmaz.
 * Ödemeye UUID ile bağlıdır ({@code fk_provider_events_payment}); ilişki nesnesi bilerek yok.
 */
@Entity
@Immutable
@Table(name = "provider_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProviderEvent {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Convert(converter = PaymentProviderTypeConverter.class)
	@Column(name = "provider", nullable = false, updatable = false, length = 16)
	private PaymentProviderType providerType;

	@Column(name = "provider_event_id", nullable = false, updatable = false,
			length = Payment.PROVIDER_REFERENCE_MAX_LENGTH)
	private String providerEventId;

	@Column(name = "payment_id", nullable = false, updatable = false)
	private UUID paymentId;

	@Convert(converter = ProviderEventTypeConverter.class)
	@Column(name = "event_type", nullable = false, updatable = false, length = 32)
	private ProviderEventType eventType;

	@Column(name = "processed_at", nullable = false, updatable = false)
	private Instant processedAt;

	/**
	 * @param providerEventId sağlayıcının olay kimliği; boş olamaz, en fazla 128 karakter
	 * @throws IllegalArgumentException olay kimliği geçersizse
	 */
	public static ProviderEvent record(PaymentProviderType providerType, String providerEventId, UUID paymentId,
			ProviderEventType eventType, Clock clock) {
		ProviderEvent event = new ProviderEvent();
		event.providerType = Objects.requireNonNull(providerType, "providerType");
		event.providerEventId = Payment.validReference(providerEventId, "providerEventId");
		event.paymentId = Objects.requireNonNull(paymentId, "paymentId");
		event.eventType = Objects.requireNonNull(eventType, "eventType");
		event.processedAt = Payment.now(clock);
		return event;
	}

}
