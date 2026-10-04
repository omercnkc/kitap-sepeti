package com.kitapsepeti.order.gateway;

/** {@link CatalogGateway#release} sonucu. */
public sealed interface ReleaseResult permits ReleaseResult.Released, ReleaseResult.AlreadyCommitted, Rejected,
		NotPerformed, Unknown {

	/**
	 * Stok bu sipariş için artık tutulmuyor: rezerv geri verildi (200), zaten {@code released}'dı (200) ya da sipariş
	 * için hiç rezervasyon yok (404 {@code RESOURCE_NOT_FOUND}; ör. reserve isteği Catalog'a hiç ulaşmadı).
	 */
	record Released() implements ReleaseResult {
	}

	/** 409 {@code RESERVATION_COMMITTED}: stok zaten düşülmüş; geri verilemez. */
	record AlreadyCommitted() implements ReleaseResult {
	}

}
