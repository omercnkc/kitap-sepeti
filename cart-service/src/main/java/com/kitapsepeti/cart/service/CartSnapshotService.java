package com.kitapsepeti.cart.service;

import java.math.RoundingMode;
import java.util.UUID;

import com.kitapsepeti.cart.dto.internal.CartSnapshotItem;
import com.kitapsepeti.cart.dto.internal.CartSnapshotResponse;
import com.kitapsepeti.cart.entity.Cart;
import com.kitapsepeti.cart.entity.CartItem;
import com.kitapsepeti.cart.entity.CartStatus;
import com.kitapsepeti.cart.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order'ın checkout'ta okuduğu sepet görüntüsü. Salt okunur: kilit yok, sepet açılmaz, zaman damgasına dokunulmaz,
 * Catalog çağrılmaz. Sepet ve satırları tek sorguda ({@link CartRepository#findByUserIdAndStatus}, satırlar eklenme sırasıyla).
 */
@Service
public class CartSnapshotService {

	private static final int MONEY_SCALE = 2;

	private final CartRepository carts;

	public CartSnapshotService(CartRepository carts) {
		this.carts = carts;
	}

	@Transactional(readOnly = true)
	public CartSnapshotResponse snapshot(UUID userId) {
		return this.carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
			.map(CartSnapshotService::toResponse)
			.orElse(CartSnapshotResponse.NO_ACTIVE_CART);
	}

	private static CartSnapshotResponse toResponse(Cart cart) {
		return new CartSnapshotResponse(cart.getId(), cart.getUpdatedAt(),
				cart.getItems().stream().map(CartSnapshotService::toItem).toList());
	}

	private static CartSnapshotItem toItem(CartItem item) {
		return new CartSnapshotItem(item.getBookId(), item.getQuantity(),
				item.getUnitPriceSnapshot().setScale(MONEY_SCALE, RoundingMode.UNNECESSARY), item.getCurrencySnapshot(),
				item.getTitleSnapshot());
	}

}
