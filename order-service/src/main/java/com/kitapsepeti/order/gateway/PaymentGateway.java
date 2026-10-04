package com.kitapsepeti.order.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/** Payment ile tek temas noktası. Exception atmaz. Ödeme oluşturma sipariş id'siyle idempotenttir (Payment sözleşmesi). */
public interface PaymentGateway {

	/** Siparişin ödemesini başlatır (Payment {@code POST /internal/payments}). */
	PaymentInitiationResult initiate(UUID orderId, UUID userId, BigDecimal amount, String currency);

}
