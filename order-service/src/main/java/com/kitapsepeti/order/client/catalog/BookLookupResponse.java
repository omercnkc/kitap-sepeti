package com.kitapsepeti.order.client.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Catalog {@code BookLookupResponse}: yalnızca okunan alanlar; bilinmeyenler yok sayılır, zorunlu alan eksikse hata. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookLookupResponse(@JsonProperty(required = true) List<Book> items) {

	public BookLookupResponse {
		Objects.requireNonNull(items, "items");
		items = List.copyOf(items);
	}

	@Override
	public String toString() {
		return "BookLookupResponse[items=" + this.items.size() + "]";
	}

	/** Catalog {@code BookSummaryResponse}. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Book(
			@JsonProperty(required = true) UUID id,
			@JsonProperty(required = true) String title,
			@JsonProperty(required = true) BigDecimal priceAmount,
			@JsonProperty(required = true) String currency,
			@JsonProperty(required = true) Boolean inStock) {

		public Book {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(title, "title");
			Objects.requireNonNull(priceAmount, "priceAmount");
			Objects.requireNonNull(currency, "currency");
			Objects.requireNonNull(inStock, "inStock");
		}

		@Override
		public String toString() {
			return "Book[redacted]";
		}

	}

}
