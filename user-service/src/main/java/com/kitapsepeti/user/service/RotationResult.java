package com.kitapsepeti.user.service;

import com.kitapsepeti.user.entity.User;

/**
 * Refresh token yenileme sonucu.
 * @param refreshToken yeni ham refresh token; yalnızca istemciye döner, saklanmaz ve loglanmaz
 */
public record RotationResult(User user, String refreshToken) {

	@Override
	public String toString() {
		return "RotationResult[userId=" + user.getId() + ", refreshToken=***]";
	}

}
