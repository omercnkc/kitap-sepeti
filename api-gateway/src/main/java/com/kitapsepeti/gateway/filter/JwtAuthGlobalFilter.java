package com.kitapsepeti.gateway.filter;

import com.kitapsepeti.gateway.config.JwtProperties;
import com.kitapsepeti.gateway.security.JwksKeyProvider;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);
	private static final String BEARER_PREFIX = "Bearer ";

	private static final List<String> PUBLIC_ANY_PATTERNS = List.of(
			"/api/auth",
			"/api/auth/**",
			"/api/search",
			"/api/search/**",
			"/api/suggest",
			"/api/suggest/**",
			"/actuator/health",
			"/actuator/health/**",
			"/webhooks",
			"/webhooks/**",
			"/api/webhooks",
			"/api/webhooks/**"
	);

	private static final List<String> PUBLIC_GET_PATTERNS = List.of(
			"/api/books",
			"/api/books/**",
			"/api/categories",
			"/api/categories/**"
	);

	private static final List<String> ADMIN_PATTERNS = List.of(
			"/api/admin",
			"/api/admin/**"
	);

	private final JwtProperties jwtProperties;
	private final JwksKeyProvider jwksKeyProvider;
	private final JsonMapper jsonMapper;
	private final PathPatternParser pathPatternParser = new PathPatternParser();

	public JwtAuthGlobalFilter(JwtProperties jwtProperties,
			JwksKeyProvider jwksKeyProvider,
			Optional<JsonMapper> jsonMapper) {
		this.jwtProperties = jwtProperties;
		this.jwksKeyProvider = jwksKeyProvider;
		this.jsonMapper = jsonMapper.orElseGet(() -> JsonMapper.builder().build());
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		if (!this.jwtProperties.isEnabled()) {
			return chain.filter(exchange);
		}

		String path = exchange.getRequest().getPath().value();
		HttpMethod method = exchange.getRequest().getMethod();

		boolean isPublicPath = isPublic(method, path);
		boolean isAdminPath = isAdminRequired(method, path);

		String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

		if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
			if (isPublicPath) {
				return chain.filter(exchange);
			}
			return onError(exchange, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
					"Kimlik doğrulaması gerekli", "Kimlik doğrulaması için geçerli bir Bearer token sağlanmalıdır.");
		}

		String token = authHeader.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty()) {
			if (isPublicPath) {
				return chain.filter(exchange);
			}
			return onError(exchange, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
					"Kimlik doğrulaması gerekli", "Bearer token boş olamaz.");
		}

		SignedJWT signedJwt;
		try {
			signedJwt = SignedJWT.parse(token);
		} catch (ParseException e) {
			log.warn("Invalid JWT token format: {}", e.getMessage());
			return onError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
					"Geçersiz kimlik doğrulama belirteci", "Belirteç biçimi geçersiz.");
		}

		Date expirationTime;
		try {
			expirationTime = signedJwt.getJWTClaimsSet().getExpirationTime();
		} catch (ParseException e) {
			return onError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
					"Geçersiz kimlik doğrulama belirteci", "Belirteç claim'leri okunamadı.");
		}

		if (expirationTime != null && expirationTime.before(new Date())) {
			log.warn("JWT token expired at: {}", expirationTime);
			return onError(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED",
					"Belirtecin süresi dolmuş", "Kimlik doğrulama belirtecinin geçerlilik süresi dolmuştur.");
		}

		String kid = signedJwt.getHeader().getKeyID();
		return this.jwksKeyProvider.getKey(kid)
				.flatMap(jwk -> {
					try {
						if (!(jwk instanceof RSAKey rsaKey)) {
							return onError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
									"Geçersiz kimlik doğrulama belirteci", "Desteklenmeyen anahtar türü.");
						}

						JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
						if (!signedJwt.verify(verifier)) {
							log.warn("JWT signature verification failed for kid: {}", kid);
							return onError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
									"Geçersiz kimlik doğrulama belirteci", "Belirteç imzası doğrulanamadı.");
						}

						JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
						String userId = claims.getSubject();
						if (userId == null || userId.isBlank()) {
							userId = claims.getStringClaim("userId");
						}

						String role = extractRole(claims);

						if (isAdminPath && !hasAdminRole(role)) {
							log.warn("Access denied for user {} with role {} to admin path {}", userId, role, path);
							return onError(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN",
									"Erişim engellendi", "Bu işlem için yönetici yetkisi gereklidir.");
						}

						ServerHttpRequest.Builder reqBuilder = exchange.getRequest().mutate();
						if (userId != null && !userId.isBlank()) {
							reqBuilder.header("X-User-Id", userId);
						}
						if (role != null && !role.isBlank()) {
							reqBuilder.header("X-User-Role", role);
						}

						return chain.filter(exchange.mutate().request(reqBuilder.build()).build());
					} catch (Exception ex) {
						log.warn("JWT validation processing error: {}", ex.getMessage());
						return onError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
								"Geçersiz kimlik doğrulama belirteci", "Belirteç doğrulama sırasında hata oluştu.");
					}
				})
				.onErrorResume(ex -> {
					log.warn("Failed to retrieve verification key: {}", ex.getMessage());
					return onError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
							"Geçersiz kimlik doğrulama belirteci", "Belirteç doğrulama anahtarı temin edilemedi.");
				});
	}

	private boolean isPublic(HttpMethod method, String path) {
		PathContainer container = PathContainer.parsePath(path);
		if (matchesAny(container, PUBLIC_ANY_PATTERNS)) {
			return true;
		}
		if (HttpMethod.GET.equals(method) && matchesAny(container, PUBLIC_GET_PATTERNS)) {
			return true;
		}
		return false;
	}

	private boolean isAdminRequired(HttpMethod method, String path) {
		PathContainer container = PathContainer.parsePath(path);
		if (matchesAny(container, ADMIN_PATTERNS)) {
			return true;
		}
		if (!HttpMethod.GET.equals(method) && matchesAny(container, PUBLIC_GET_PATTERNS)) {
			return true;
		}
		return false;
	}

	private boolean matchesAny(PathContainer container, List<String> patterns) {
		for (String pattern : patterns) {
			if (this.pathPatternParser.parse(pattern).matches(container)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasAdminRole(String role) {
		if (role == null || role.isBlank()) {
			return false;
		}
		for (String part : role.split(",")) {
			String trimmed = part.trim();
			if ("ROLE_ADMIN".equalsIgnoreCase(trimmed) || "ADMIN".equalsIgnoreCase(trimmed)) {
				return true;
			}
		}
		return false;
	}

	private String extractRole(JWTClaimsSet claims) throws ParseException {
		Object roleClaim = claims.getClaim("role");
		if (roleClaim == null) {
			roleClaim = claims.getClaim("roles");
		}
		if (roleClaim instanceof List<?> list) {
			if (!list.isEmpty()) {
				return String.valueOf(list.get(0));
			}
			return null;
		}
		if (roleClaim != null) {
			return roleClaim.toString();
		}
		return null;
	}

	private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String code, String title, String detail) {
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(status);
		response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", title);
		body.put("status", status.value());
		body.put("detail", detail);
		body.put("instance", exchange.getRequest().getPath().value());
		body.put("code", code);

		byte[] bytes;
		try {
			bytes = this.jsonMapper.writeValueAsBytes(body);
		} catch (Exception ex) {
			bytes = ("{\"status\":" + status.value() + ",\"title\":\"" + title + "\",\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
		}

		DataBuffer buffer = response.bufferFactory().wrap(bytes);
		return response.writeWith(Mono.just(buffer));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

}
