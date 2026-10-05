package com.kitapsepeti.order.gateway;

import java.util.Objects;
import java.util.UUID;

/**
 * {@link PaymentGateway#initiate} sonucu. 409 {@code PAYMENT_ORDER_MISMATCH} {@link Rejected}; 503
 * {@code PAYMENT_PROVIDER_UNAVAILABLE} {@link Unknown} (Payment'ta ödeme satırı oluşmuş olabilir, tekrar istek tamamlar).
 */
public sealed interface PaymentInitiationResult permits PaymentInitiationResult.Initiated, Rejected, NotPerformed,
		Unknown {

	/**
	 * Siparişin ödemesi var: yeni (201) ya da aynı istekle tekrar (200, mevcut ödeme). Tekrarda ödeme sonuçlanmış
	 * olabilir; {@code state} bunu gösterir. {@code failureCode} Payment'ın ret kodu (yalnızca failed'da, doğrulanmamış).
	 */
	record Initiated(UUID paymentId, PaymentState state, String failureCode) implements PaymentInitiationResult {

		public Initiated {
			Objects.requireNonNull(paymentId, "paymentId");
			Objects.requireNonNull(state, "state");
		}

		public Initiated(UUID paymentId, PaymentState state) {
			this(paymentId, state, null);
		}

		@Override
		public String toString() {
			return "Initiated[state=" + this.state + "]";
		}

	}

}
