package com.kitapsepeti.user.entity;

/**
 * Kullanıcı hesap durumu. DB'de küçük harfle saklanır ({@link UserStatusConverter}).
 */
public enum UserStatus {
	/** Hesap kullanılabilir durumda. */
	ACTIVE,
	/** Hesap askıya alınmış; giriş yapamaz. */
	SUSPENDED
}
