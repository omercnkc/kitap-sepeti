package com.kitapsepeti.cart.entity;

/**
 * Sepetin durumu. DB'de küçük harfle saklanır ({@link CartStatusConverter}).
 * Geçişler yalnızca {@link #ACTIVE}'den: {@link Cart#checkout()} ve {@link Cart#abandon()}.
 */
public enum CartStatus {
	/** Kullanıcının üzerinde çalıştığı sepet; kullanıcı başına en fazla bir tane ({@code uk_carts_active_user}). */
	ACTIVE,
	/** Siparişe dönüştü; geçmiş olarak kalır. */
	CHECKED_OUT,
	/** Terk edildi; geçmiş olarak kalır. */
	ABANDONED
}
