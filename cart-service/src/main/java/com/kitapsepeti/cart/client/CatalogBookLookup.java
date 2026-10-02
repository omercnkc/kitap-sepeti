package com.kitapsepeti.cart.client;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Catalog {@code BookLookupResponse}: istenenlerden yayındaki kitaplar. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogBookLookup(@JsonProperty(required = true) List<CatalogBook> items) {

	public CatalogBookLookup {
		Objects.requireNonNull(items, "items");
	}

}
