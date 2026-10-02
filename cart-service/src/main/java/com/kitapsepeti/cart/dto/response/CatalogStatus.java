package com.kitapsepeti.cart.dto.response;

/** Yanıttaki fiyat/stok bilgisinin Catalog'la doğrulanıp doğrulanamadığı. */
public enum CatalogStatus {

	/** Catalog'a ulaşıldı; {@code currentUnitPrice} ve {@code available} doludur. */
	VERIFIED,

	/** Catalog'a ulaşılamadı; satırlarda {@code available} ve {@code currentUnitPrice} null, tutarlar anlık görüntüden. */
	UNAVAILABLE

}
