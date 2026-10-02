package com.kitapsepeti.cart.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Yanıttaki fiyat/stok bilgisinin Catalog'la doğrulanıp doğrulanamadığı. */
@Schema(enumAsRef = true, description = "`VERIFIED`: Catalog'a ulaşıldı; satırlarda `available` ve "
		+ "`currentUnitPrice` güncel. `UNAVAILABLE`: Catalog'a ulaşılamadı; satırlarda `available` ve "
		+ "`currentUnitPrice` null, tutarlar anlık görüntüden.")
public enum CatalogStatus {

	/** Catalog'a ulaşıldı; {@code currentUnitPrice} ve {@code available} doludur. */
	VERIFIED,

	/** Catalog'a ulaşılamadı; satırlarda {@code available} ve {@code currentUnitPrice} null, tutarlar anlık görüntüden. */
	UNAVAILABLE

}
