package com.kitapsepeti.user.mapper;

import com.kitapsepeti.user.dto.request.UpdateProfileRequest;
import com.kitapsepeti.user.dto.response.UserResponse;
import com.kitapsepeti.user.entity.User;
import com.kitapsepeti.user.validation.TrPhones;

/** {@link User} ↔ DTO dönüşümleri. */
public final class UserMapper {

	private UserMapper() {
	}

	public static UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
				user.getPhone(), user.getRole(), user.getStatus());
	}

	/** null alanlar değiştirilmez; {@code phone == ""} telefonu siler; doluysa kanonik 5xxxxxxxxx. */
	public static void applyUpdate(User user, UpdateProfileRequest request) {
		if (request.firstName() != null) {
			user.setFirstName(request.firstName());
		}
		if (request.lastName() != null) {
			user.setLastName(request.lastName());
		}
		if (request.phone() != null) {
			user.setPhone(TrPhones.toCanonicalOrNull(request.phone()));
		}
	}

}
