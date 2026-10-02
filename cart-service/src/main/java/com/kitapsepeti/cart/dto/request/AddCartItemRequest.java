package com.kitapsepeti.cart.dto.request;

import java.util.UUID;

import com.kitapsepeti.cart.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Sepete kitap ekleme. {@code quantity} verilmezse 1; kitap sepetteyse mevcut adede eklenir. DB sınırı
 * ({@value CartItem#MAX_QUANTITY}) dışı 400; iş limiti ({@code app.cart.max-quantity-per-item}) serviste 409.
 */
public record AddCartItemRequest(@NotNull UUID bookId,
		@Min(CartItem.MIN_QUANTITY) @Max(CartItem.MAX_QUANTITY) Integer quantity) {

	public static final int DEFAULT_QUANTITY = 1;

	public AddCartItemRequest {
		if (quantity == null) {
			quantity = DEFAULT_QUANTITY;
		}
	}

}
