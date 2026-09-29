package com.kitapsepeti.user.controller;

import com.kitapsepeti.user.dto.request.LoginRequest;
import com.kitapsepeti.user.dto.request.RefreshRequest;
import com.kitapsepeti.user.dto.request.RegisterRequest;
import com.kitapsepeti.user.dto.response.TokenResponse;
import com.kitapsepeti.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kimlik uçları. Token içeren yanıtlar {@code Cache-Control: no-store} ile döner (RFC 6749 §5.1);
 * ara katmanlar veya tarayıcı token'ı önbelleğe almaz.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
		return tokens(HttpStatus.CREATED, authService.register(request));
	}

	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return tokens(HttpStatus.OK, authService.login(request));
	}

	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return tokens(HttpStatus.OK, authService.refresh(request));
	}

	private static ResponseEntity<TokenResponse> tokens(HttpStatus status, TokenResponse body) {
		return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
	}

}
