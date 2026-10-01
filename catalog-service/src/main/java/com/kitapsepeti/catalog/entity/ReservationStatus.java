package com.kitapsepeti.catalog.entity;

/**
 * Stok rezervasyonunun durumu. DB'de küçük harfle saklanır ({@link ReservationStatusConverter}).
 */
public enum ReservationStatus {
	/** Stok sipariş için ayrıldı; süresi ({@code expiresAt}) dolarsa serbest bırakılır. */
	HELD,
	/** Sipariş onaylandı; ayrılan stok kalıcı olarak düşüldü. */
	COMMITTED,
	/** Rezervasyon iptal edildi veya süresi doldu; stok geri verildi. */
	RELEASED
}
