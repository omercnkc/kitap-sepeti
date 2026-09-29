package com.kitapsepeti.user.controller;

import static com.kitapsepeti.user.config.OpenApiConfig.PROBLEM_JSON;
import static com.kitapsepeti.user.config.OpenApiConfig.PROBLEM_SCHEMA_REF;

import java.util.UUID;

import com.kitapsepeti.user.dto.request.UpdateProfileRequest;
import com.kitapsepeti.user.dto.response.UserResponse;
import com.kitapsepeti.user.security.CurrentUserId;
import com.kitapsepeti.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Oturum sahibinin profili; kullanıcı id'si yalnızca token'dan gelir. */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Profile", description = "Oturum sahibinin profili. Kullanıcı token'dan belirlenir.")
@ApiResponse(responseCode = "403", description = "`ACCOUNT_SUSPENDED`: hesap askıya alınmış.",
		content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_SCHEMA_REF)))
public class MeController {

	private final UserService userService;

	public MeController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	@Operation(operationId = "getProfile", summary = "Profilimi getir")
	@ApiResponse(responseCode = "200", description = "Profil.")
	public UserResponse get(@CurrentUserId UUID userId) {
		return userService.getProfile(userId);
	}

	@PatchMapping
	@Operation(operationId = "updateProfile", summary = "Profilimi güncelle",
			description = "Kısmi güncelleme: yalnızca gönderilen alanlar değişir.")
	@ApiResponse(responseCode = "200", description = "Güncellenmiş profil.")
	public UserResponse update(@CurrentUserId UUID userId, @Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(userId, request);
	}

}
