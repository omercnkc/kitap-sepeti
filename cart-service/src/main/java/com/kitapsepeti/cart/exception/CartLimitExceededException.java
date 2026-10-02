package com.kitapsepeti.cart.exception;

import com.kitapsepeti.common.error.ApiException;

/**
 * Sepet limiti aşıldı (409). Hangi limit olduğu koddan anlaşılır: {@link CartErrorCode#CART_LINE_LIMIT_EXCEEDED}
 * (farklı kitap sayısı) ya da {@link CartErrorCode#CART_QUANTITY_LIMIT_EXCEEDED} (kitap başına adet).
 * Yanıta yalnızca yapılandırılmış üst sınır ({@code limit}) eklenir; istenen değer ve kitap id'si eklenmez.
 */
public class CartLimitExceededException extends ApiException {

	private final int limit;

	private CartLimitExceededException(CartErrorCode code, int limit) {
		super(code);
		this.limit = limit;
	}

	public static CartLimitExceededException lines(int maxLines) {
		return new CartLimitExceededException(CartErrorCode.CART_LINE_LIMIT_EXCEEDED, maxLines);
	}

	public static CartLimitExceededException quantityPerItem(int maxQuantityPerItem) {
		return new CartLimitExceededException(CartErrorCode.CART_QUANTITY_LIMIT_EXCEEDED, maxQuantityPerItem);
	}

	public int getLimit() {
		return this.limit;
	}

}
