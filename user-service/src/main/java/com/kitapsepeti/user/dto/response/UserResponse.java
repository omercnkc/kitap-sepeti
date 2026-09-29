package com.kitapsepeti.user.dto.response;

import java.util.UUID;

import com.kitapsepeti.user.entity.Role;
import com.kitapsepeti.user.entity.UserStatus;

/** Profil yanıtı. Parola hash'i bilinçli olarak yok. */
public record UserResponse(UUID id, String email, String firstName, String lastName, String phone, Role role,
		UserStatus status) {

}
