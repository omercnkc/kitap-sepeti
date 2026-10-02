package com.kitapsepeti.cart.dto.request;

import com.kitapsepeti.cart.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Sepetteki kitabın yeni adedi (eklenmez, yerine konur). DB sınırı ({@value CartItem#MAX_QUANTITY}) dışı 400; iş limiti
 * ({@code app.cart.max-quantity-per-item}) serviste 409.
 */
public record UpdateCartItemRequest(@NotNull @Min(CartItem.MIN_QUANTITY) @Max(CartItem.MAX_QUANTITY) Integer quantity) {
}
