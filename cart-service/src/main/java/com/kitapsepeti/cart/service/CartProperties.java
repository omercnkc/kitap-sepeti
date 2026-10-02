package com.kitapsepeti.cart.service;

import com.kitapsepeti.cart.client.CatalogGateway;
import com.kitapsepeti.cart.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Sepet sınırları ({@code app.cart.*}); aşan değerle uygulama açılmaz.
 *
 * @param maxQuantityPerItem bir satırdaki en fazla adet; DB'deki {@code ck_cart_items_quantity} (1–99) sınırını aşamaz
 * @param maxLines bir sepetteki en fazla farklı kitap sayısı; sepet görünümü tüm satırları tek Catalog toplu okumasıyla
 * doğruladığı için {@link CatalogGateway#MAX_LOOKUP_IDS}'i aşamaz
 */
@Validated
@ConfigurationProperties(prefix = "app.cart")
public record CartProperties(
		@Min(CartItem.MIN_QUANTITY) @Max(CartItem.MAX_QUANTITY) int maxQuantityPerItem,
		@Min(1) @Max(CatalogGateway.MAX_LOOKUP_IDS) int maxLines) {
}
