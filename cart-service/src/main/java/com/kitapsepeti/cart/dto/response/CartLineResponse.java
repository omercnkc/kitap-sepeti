package com.kitapsepeti.cart.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sepet satırı. Para alanları scale 2.
 * @param snapshotUnitPrice kitap sepete eklendiğinde (ya da en son tekrar eklendiğinde) geçerli fiyat
 * @param currentUnitPrice Catalog'daki güncel fiyat; Catalog'a ulaşılamadıysa ya da kitap satışta değilse null
 * @param available Catalog'da var ve stokta ise true; yoksa ya da stokta değilse false; Catalog'a ulaşılamadıysa null
 * @param priceChanged güncel fiyat biliniyor ve anlık görüntüden farklı
 * @param lineTotal {@code quantity × (currentUnitPrice ?: snapshotUnitPrice)}
 */
public record CartLineResponse(UUID bookId, String title, String coverUrl, int quantity, String currency,
		BigDecimal snapshotUnitPrice, BigDecimal currentUnitPrice, Boolean available, boolean priceChanged,
		BigDecimal lineTotal) {
}
