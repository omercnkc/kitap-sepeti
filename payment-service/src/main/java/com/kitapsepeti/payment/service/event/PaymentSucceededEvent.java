package com.kitapsepeti.payment.service.event;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code PaymentSucceeded} outbox olayının içeriği (bkz. {@code docs/events/payment-succeeded.md}).
 * {@code eventId} outbox satırının id'sidir (= mesajın {@code message_id}'si). Tutar, ondalık kaybı olmasın diye
 * metindir ({@code "149.90"}): outbox payload kolonu MySQL JSON ve sayıları DOUBLE saklar. Kullanıcı id'si
 * bilinçli olarak yoktur. Alan eklemek geriye uyumludur; alan silmek veya anlamını değiştirmek
 * {@code eventVersion}'ı artırmayı gerektirir.
 */
public record PaymentSucceededEvent(int eventVersion, UUID eventId, UUID paymentId, UUID orderId, String amount,
		String currency, Instant occurredAt) {

	public static final int VERSION = 1;

	public static final String TYPE = "PaymentSucceeded";

}
