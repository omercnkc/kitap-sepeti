package com.kitapsepeti.order.client.catalog;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Catalog {@code ReservationResponse}: yalnızca okunan alanlar (kalemler okunmaz; sipariş kalemleri Order'da). Durum
 * metin olarak okunur; tanınmayan değer gateway'de sözleşme ihlalidir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationResponse(
		@JsonProperty(required = true) UUID orderId,
		@JsonProperty(required = true) String status,
		@JsonProperty(required = true) Instant expiresAt) {

	public ReservationResponse {
		Objects.requireNonNull(orderId, "orderId");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(expiresAt, "expiresAt");
	}

	@Override
	public String toString() {
		return "ReservationResponse[status=" + this.status + "]";
	}

}
