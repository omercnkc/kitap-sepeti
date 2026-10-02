package com.kitapsepeti.cart.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sepet satırı. Para alanları scale 2.
 * @param snapshotUnitPrice kitap sepete eklendiğinde (ya da en son tekrar eklendiğinde) geçerli fiyat
 * @param currentUnitPrice Catalog'daki güncel fiyat; Catalog'a ulaşılamadıysa ya da kitap satışta değilse null
 * @param available Catalog'da var ve stokta ise true; yoksa ya da stokta değilse false; Catalog'a ulaşılamadıysa null
 * @param priceChanged güncel fiyat biliniyor ve anlık görüntüden farklı
 * @param lineTotal {@code quantity × (currentUnitPrice ?: snapshotUnitPrice)}
 */
public record CartLineResponse(
		@Schema(requiredMode = REQUIRED) UUID bookId,
		@Schema(requiredMode = REQUIRED, description = "Sepete eklendiği andaki başlık.") String title,
		@Schema(requiredMode = REQUIRED, types = { "string", "null" },
				description = "Sepete eklendiği andaki kapak adresi; kapak yoksa null.") String coverUrl,
		@Schema(requiredMode = REQUIRED, minimum = "1", maximum = "99") int quantity,
		@Schema(requiredMode = REQUIRED, description = "Anlık görüntünün para birimi.") String currency,
		@Schema(requiredMode = REQUIRED, description = "Kitap sepete eklendiğinde (ya da en son tekrar eklendiğinde) "
				+ "geçerli birim fiyat, 2 ondalık basamak.") BigDecimal snapshotUnitPrice,
		@Schema(requiredMode = REQUIRED, types = { "number", "null" }, description = "Catalog'daki güncel birim fiyat, "
				+ "2 ondalık basamak; Catalog'a ulaşılamadıysa ya da kitap satışta değilse null.")
		BigDecimal currentUnitPrice,
		@Schema(requiredMode = REQUIRED, types = { "boolean", "null" }, description = "Catalog'da satışta ve stokta "
				+ "ise true, değilse false; Catalog'a ulaşılamadıysa (`catalogStatus` = `UNAVAILABLE`) null.")
		Boolean available,
		@Schema(requiredMode = REQUIRED, description = "Güncel fiyat biliniyor ve anlık görüntüden farklı.")
		boolean priceChanged,
		@Schema(requiredMode = REQUIRED, description = "`quantity` × (`currentUnitPrice`, null ise "
				+ "`snapshotUnitPrice`), 2 ondalık basamak.") BigDecimal lineTotal) {
}
