package com.kitapsepeti.order.client.payment;

import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payment {@code PaymentResponse}: yalnızca okunan alanlar (yönlendirme adresi, tutar okunmaz). Bilinmeyen alanlar yok
 * sayılır; zorunlu alan eksikse çözümleme başarısız olur. {@code failureCode} sözleşmede zorunlu ama nullable: yalnızca
 * failed ödemede dolu.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResponse(
		@JsonProperty(required = true) UUID paymentId,
		@JsonProperty(required = true) UUID orderId,
		@JsonProperty(required = true) String status,
		@JsonProperty(required = true) String failureCode) {

	public PaymentResponse {
		Objects.requireNonNull(paymentId, "paymentId");
		Objects.requireNonNull(orderId, "orderId");
		Objects.requireNonNull(status, "status");
	}

	@Override
	public String toString() {
		return "PaymentResponse[status=" + this.status + "]";
	}

}
