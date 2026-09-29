package com.kitapsepeti.user.service.event;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code UserRegistered} outbox olayının içeriği. Alan eklemek geriye uyumludur; alan silmek veya
 * anlamını değiştirmek {@code eventVersion}'ı artırmayı gerektirir. Parola/hash ASLA eklenmez.
 */
public record UserRegisteredEvent(int eventVersion, UUID userId, String email, String firstName,
		Instant occurredAt) {

	public static final int VERSION = 1;

	public static final String TYPE = "UserRegistered";

}
