package com.kitapsepeti.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {

	@Override
	public String toString() {
		return "RefreshRequest[refreshToken=***]";
	}

}
