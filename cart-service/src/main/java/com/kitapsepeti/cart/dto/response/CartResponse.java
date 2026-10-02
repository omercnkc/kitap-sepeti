package com.kitapsepeti.cart.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kullanıcının aktif sepeti (yoksa boş sepet). Sepet id'si, satır id'si ve userId bilerek yok.
 * @param items eklenme sırasıyla
 * @param lineCount satır (farklı kitap) sayısı
 * @param itemCount adetlerin toplamı
 * @param subtotal {@code available != false} satırların {@code lineTotal} toplamı, scale 2; satırların para birimi
 * farklıysa null
 * @param currency tüm satırlarda ortak para birimi; sepet boşsa ya da birimler farklıysa null
 */
public record CartResponse(List<CartLineResponse> items, int lineCount, int itemCount, BigDecimal subtotal,
		String currency, CatalogStatus catalogStatus) {

	private static final CartResponse EMPTY = new CartResponse(List.of(), 0, 0, new BigDecimal("0.00"), null,
			CatalogStatus.VERIFIED);

	public CartResponse {
		items = List.copyOf(items);
	}

	public static CartResponse empty() {
		return EMPTY;
	}

}
