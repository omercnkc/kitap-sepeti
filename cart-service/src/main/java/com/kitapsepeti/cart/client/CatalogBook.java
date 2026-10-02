package com.kitapsepeti.cart.client;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Catalog'un kitap yanıtından ({@code BookDetailResponse} / {@code BookSummaryResponse}) sepetin kullandığı alanlar.
 * Diğer alanlar yok sayılır. Catalog'da zorunlu olan alan eksik ya da null gelirse okuma başarısız olur ve
 * {@link CatalogGateway} bunu Catalog kesintisi sayar.
 *
 * @param coverUrl Catalog'da da opsiyonel; null olabilir
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogBook(
		@JsonProperty(required = true) UUID id,
		@JsonProperty(required = true) String title,
		@JsonProperty(required = true) BigDecimal priceAmount,
		@JsonProperty(required = true) String currency,
		String coverUrl,
		@JsonProperty(required = true) boolean inStock) {

	public CatalogBook {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(priceAmount, "priceAmount");
		Objects.requireNonNull(currency, "currency");
	}

}
