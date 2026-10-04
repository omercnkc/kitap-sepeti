package com.kitapsepeti.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderEventTest {

	private static final Clock T1 = Clock.fixed(Instant.parse("2026-03-01T10:15:30.123456789Z"), ZoneOffset.UTC);

	private final UUID paymentId = UUID.randomUUID();

	@Test
	void recordKeepsValuesAndClockTimeInMicros() {
		ProviderEvent event = ProviderEvent.record(PaymentProviderType.MOCK, "evt_1", paymentId,
				ProviderEventType.PAYMENT_SUCCEEDED, T1);

		assertThat(event.getId()).isNull();
		assertThat(event.getProviderType()).isEqualTo(PaymentProviderType.MOCK);
		assertThat(event.getProviderEventId()).isEqualTo("evt_1");
		assertThat(event.getPaymentId()).isEqualTo(paymentId);
		assertThat(event.getEventType()).isEqualTo(ProviderEventType.PAYMENT_SUCCEEDED);
		assertThat(event.getProcessedAt()).isEqualTo(Instant.parse("2026-03-01T10:15:30.123456Z"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " " })
	void rejectsBlankEventId(String eventId) {
		assertThatThrownBy(() -> ProviderEvent.record(PaymentProviderType.MOCK, eventId, paymentId,
				ProviderEventType.PAYMENT_FAILED, T1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void eventIdMayBeUpTo128Characters() {
		assertThatThrownBy(() -> ProviderEvent.record(PaymentProviderType.MOCK, "e".repeat(129), paymentId,
				ProviderEventType.PAYMENT_FAILED, T1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(ProviderEvent.record(PaymentProviderType.MOCK, "e".repeat(128), paymentId,
				ProviderEventType.PAYMENT_FAILED, T1).getProviderEventId()).hasSize(128);
	}

	@Test
	void requiresProviderPaymentAndType() {
		assertThatThrownBy(() -> ProviderEvent.record(null, "evt", paymentId, ProviderEventType.PAYMENT_FAILED, T1))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> ProviderEvent.record(PaymentProviderType.MOCK, "evt", null,
				ProviderEventType.PAYMENT_FAILED, T1))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> ProviderEvent.record(PaymentProviderType.MOCK, "evt", paymentId, null, T1))
			.isInstanceOf(NullPointerException.class);
	}

}
