package com.kitapsepeti.cart.dto.request;

import com.kitapsepeti.cart.entity.CartItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Sepetteki kitabın yeni adedi (eklenmez, yerine konur). DB sınırı ({@value CartItem#MAX_QUANTITY}) dışı 400; iş limiti
 * ({@code app.cart.max-quantity-per-item}) serviste 409.
 */
public record UpdateCartItemRequest(
		@Schema(description = "Yeni adet (mevcut adedin yerine). 1–99 dışı 400 `VALIDATION_FAILED`; kitap başına iş "
				+ "sınırını (varsayılan 10) aşarsa 409 `CART_QUANTITY_LIMIT_EXCEEDED`.")
		@NotNull @Min(CartItem.MIN_QUANTITY) @Max(CartItem.MAX_QUANTITY) Integer quantity) {
}
