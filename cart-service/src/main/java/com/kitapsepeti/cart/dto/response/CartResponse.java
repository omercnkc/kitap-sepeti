package com.kitapsepeti.cart.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kullanıcının aktif sepeti (yoksa boş sepet). Sepet id'si, satır id'si ve userId bilerek yok.
 * @param items eklenme sırasıyla
 * @param lineCount satır (farklı kitap) sayısı
 * @param itemCount adetlerin toplamı
 * @param subtotal {@code available != false} satırların {@code lineTotal} toplamı, scale 2; satırların para birimi
 * farklıysa null
 * @param currency tüm satırlarda ortak para birimi; sepet boşsa ya da birimler farklıysa null
 */
public record CartResponse(
		@Schema(requiredMode = REQUIRED, description = "Satırlar, eklenme sırasıyla.") List<CartLineResponse> items,
		@Schema(requiredMode = REQUIRED, description = "Satır (farklı kitap) sayısı.") int lineCount,
		@Schema(requiredMode = REQUIRED, description = "Adetlerin toplamı.") int itemCount,
		@Schema(requiredMode = REQUIRED, types = { "number", "null" }, description = "`available` değeri `false` "
				+ "olmayan satırların `lineTotal` toplamı, 2 ondalık basamak; boş sepette `0.00`. Satırların para "
				+ "birimi farklıysa null.") BigDecimal subtotal,
		@Schema(requiredMode = REQUIRED, types = { "string", "null" }, description = "Tüm satırlarda ortak para "
				+ "birimi; sepet boşsa ya da birimler farklıysa null.") String currency,
		@Schema(requiredMode = REQUIRED) CatalogStatus catalogStatus) {

	private static final CartResponse EMPTY = new CartResponse(List.of(), 0, 0, new BigDecimal("0.00"), null,
			CatalogStatus.VERIFIED);

	public CartResponse {
		items = List.copyOf(items);
	}

	public static CartResponse empty() {
		return EMPTY;
	}

}
