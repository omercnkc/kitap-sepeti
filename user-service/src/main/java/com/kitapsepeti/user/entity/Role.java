package com.kitapsepeti.user.entity;

/**
 * Kullanıcı yetki rolü. DB'de enum adıyla aynen ({@code 'USER'}, {@code 'ADMIN'}) saklanır.
 */
public enum Role {
	/** Standart müşteri. */
	USER,
	/** Yönetim paneli yetkisi olan kullanıcı. */
	ADMIN
}
