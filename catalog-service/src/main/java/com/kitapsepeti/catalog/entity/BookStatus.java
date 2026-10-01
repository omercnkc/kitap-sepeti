package com.kitapsepeti.catalog.entity;

/**
 * Kitabın yayın durumu. DB'de küçük harfle saklanır ({@link BookStatusConverter}).
 */
public enum BookStatus {
	/** Hazırlanıyor; katalogda görünmez. */
	DRAFT,
	/** Yayında; listelenir ve satılabilir. */
	PUBLISHED,
	/** Satıştan kaldırıldı; geçmiş siparişler için kayıt korunur. */
	ARCHIVED
}
