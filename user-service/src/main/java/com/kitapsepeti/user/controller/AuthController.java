package com.kitapsepeti.user.controller;

import static com.kitapsepeti.user.config.OpenApiConfig.PROBLEM_JSON;
import static com.kitapsepeti.user.config.OpenApiConfig.PROBLEM_SCHEMA_REF;

import com.kitapsepeti.user.dto.request.LoginRequest;
import com.kitapsepeti.user.dto.request.RefreshRequest;
import com.kitapsepeti.user.dto.request.RegisterRequest;
import com.kitapsepeti.user.dto.response.TokenResponse;
import com.kitapsepeti.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Auth", description = "Kayıt, giriş ve token yenileme. Token gerektirmez.")
@SecurityRequirements
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@Operation(operationId = "register", summary = "Kayıt ol",
			description = "Yeni kullanıcı oluşturur ve doğrudan oturum açar (token çifti döner).")
	@ApiResponse(responseCode = "201", description = "Kullanıcı oluşturuldu.")
	@ApiResponse(responseCode = "409", description = "`EMAIL_ALREADY_EXISTS`: e-posta zaten kayıtlı.",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_SCHEMA_REF),
					examples = @ExampleObject(value = """
							{"title":"Conflict","status":409,"detail":"Email is already registered.",\
							"instance":"/api/auth/register","code":"EMAIL_ALREADY_EXISTS"}""")))
	public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
		return tokens(HttpStatus.CREATED, authService.register(request));
	}

	@PostMapping("/login")
	@Operation(operationId = "login", summary = "Giriş yap", description = "E-posta ve parola ile token çifti alır.")
	@ApiResponse(responseCode = "200", description = "Giriş başarılı.")
	@ApiResponse(responseCode = "401", description = "`INVALID_CREDENTIALS`: e-posta veya parola hatalı "
			+ "(hangisinin hatalı olduğu bilinçli olarak söylenmez).",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_SCHEMA_REF),
					examples = @ExampleObject(value = """
							{"title":"Unauthorized","status":401,"detail":"Invalid email or password.",\
							"instance":"/api/auth/login","code":"INVALID_CREDENTIALS"}""")))
	@ApiResponse(responseCode = "403", description = "`ACCOUNT_SUSPENDED`: hesap askıya alınmış (yalnızca parola doğruysa).",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_SCHEMA_REF)))
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return tokens(HttpStatus.OK, authService.login(request));
	}

	@PostMapping("/refresh")
	@Operation(operationId = "refresh", summary = "Token yenile", description = """
			Refresh token karşılığında yeni bir access + refresh token çifti döner. Her çağrı yeni bir refresh \
			token üretir ve gönderileni iptal eder (rotation); istemci yanıttaki yeni token'ı saklamalıdır. \
			İptal edilmiş (eski) bir refresh token tekrar kullanılırsa token çalınmış sayılır ve kullanıcının \
			tüm oturumları (tüm refresh token'ları) iptal edilir.""")
	@ApiResponse(responseCode = "200", description = "Yeni token çifti.")
	@ApiResponse(responseCode = "401", description = "`INVALID_REFRESH_TOKEN`: token bilinmiyor, süresi dolmuş "
			+ "veya iptal edilmiş.",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_SCHEMA_REF)))
	public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return tokens(HttpStatus.OK, authService.refresh(request));
	}

	private static ResponseEntity<TokenResponse> tokens(HttpStatus status, TokenResponse body) {
		return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
	}

}
