package com.kitapsepeti.order.security;

import java.util.Optional;
import java.util.UUID;

/**
 * JWT {@code sub} = user-service'teki kullanıcı UUID'si ({@link UUID#toString()} biçimi, küçük harf). Yalnızca bu
 * biçim kabul edilir: {@link UUID#fromString} "1-1-1-1-1" gibi yazımları da çözer; aynı kullanıcıya iki farklı sub düşmesin.
 */
public final class JwtSubjects {

	private JwtSubjects() {
	}

	public static Optional<UUID> userId(String subject) {
		if (subject == null) {
			return Optional.empty();
		}
		try {
			UUID id = UUID.fromString(subject);
			return id.toString().equals(subject) ? Optional.of(id) : Optional.empty();
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
