package com.kitapsepeti.user.controller;

import java.util.UUID;

import com.kitapsepeti.user.dto.request.UpdateProfileRequest;
import com.kitapsepeti.user.dto.response.UserResponse;
import com.kitapsepeti.user.security.CurrentUserId;
import com.kitapsepeti.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Oturum sahibinin profili; kullanıcı id'si yalnızca token'dan gelir. */
@RestController
@RequestMapping("/api/me")
public class MeController {

	private final UserService userService;

	public MeController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public UserResponse get(@CurrentUserId UUID userId) {
		return userService.getProfile(userId);
	}

	@PatchMapping
	public UserResponse update(@CurrentUserId UUID userId, @Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(userId, request);
	}

}
