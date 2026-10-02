package com.kitapsepeti.cart.dto.request;

import java.util.UUID;

import com.kitapsepeti.cart.entity.CartItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Sepete kitap ekleme. {@code quantity} verilmezse 1; kitap sepetteyse mevcut adede eklenir. DB sınırı
 * ({@value CartItem#MAX_QUANTITY}) dışı 400; iş limiti ({@code app.cart.max-quantity-per-item}) serviste 409.
 */
public record AddCartItemRequest(@NotNull UUID bookId,
		@Schema(defaultValue = "1", description = "Eklenecek adet; kitap sepetteyse mevcut adede eklenir. 1–99 dışı "
				+ "400 `VALIDATION_FAILED`; kitap başına iş sınırını (varsayılan 10) aşarsa 409 "
				+ "`CART_QUANTITY_LIMIT_EXCEEDED`.")
		@Min(CartItem.MIN_QUANTITY) @Max(CartItem.MAX_QUANTITY) Integer quantity) {

	public static final int DEFAULT_QUANTITY = 1;

	public AddCartItemRequest {
		if (quantity == null) {
			quantity = DEFAULT_QUANTITY;
		}
	}

}
