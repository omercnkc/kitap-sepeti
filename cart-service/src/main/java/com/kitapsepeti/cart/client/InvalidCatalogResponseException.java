package com.kitapsepeti.cart.client;

/** Catalog 2xx döndü ama yanıt beklenen kitap(lar)ı taşımıyor; {@code CatalogUnavailableException}'ın nedeni olur. */
class InvalidCatalogResponseException extends RuntimeException {

	InvalidCatalogResponseException(String message) {
		super(message);
	}

}
