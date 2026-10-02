package com.kitapsepeti.cart.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Sepet sınırları ({@code app.cart.*}).
 *
 * @param maxQuantityPerItem bir satırdaki en fazla adet; DB'deki {@code ck_cart_items_quantity} (1–99) sınırını aşamaz
 * @param maxLines bir sepetteki en fazla farklı kitap sayısı
 */
@Validated
@ConfigurationProperties(prefix = "app.cart")
public record CartProperties(@Min(1) @Max(99) int maxQuantityPerItem, @Min(1) int maxLines) {
}
