package com.kitapsepeti.cart.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.cart.entity.Cart;
import com.kitapsepeti.cart.entity.CartItem;

/**
 * Sepetin transaction içinde kopyalanmış, entity'den bağımsız satırları (eklenme sırasıyla). Transaction kapandıktan
 * sonra Catalog'la birleştirilir ({@link CartViewAssembler}). Sepet id'si, satır id'si ve userId bilerek yok.
 */
public record CartContents(List<Line> lines) {

	public static final CartContents EMPTY = new CartContents(List.of());

	public CartContents {
		lines = List.copyOf(lines);
	}

	static CartContents of(Cart cart) {
		return new CartContents(cart.getItems().stream().map(Line::of).toList());
	}

	/** @param unitPrice sepete eklendiği (ya da en son yenilendiği) andaki fiyat, scale 2 */
	public record Line(UUID bookId, String title, String coverUrl, int quantity, String currency, BigDecimal unitPrice) {

		static Line of(CartItem item) {
			return new Line(item.getBookId(), item.getTitleSnapshot(), item.getCoverUrlSnapshot(), item.getQuantity(),
					item.getCurrencySnapshot(), item.getUnitPriceSnapshot());
		}

	}

}
