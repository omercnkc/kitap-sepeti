package com.kitapsepeti.common.error;

import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

/**
 * Ortak handler'ların (MVC hataları, doğrulama, DB çakışması, 401/403/503, beklenmeyen hata) ürettiği kodlar.
 * Sıra anlam taşımaz; OpenAPI'deki sıra her servisin kendi listesinden gelir. AUTHENTICATION_UNAVAILABLE
 * yalnızca JWKS ile doğrulayan servislerde ({@code ProblemDetailAuthenticationFailureHandler}) oluşur.
 */
public enum CommonErrorCode implements ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, Level.INFO, "Request validation failed."),
	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, Level.INFO, "Request could not be read."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, Level.INFO, "Authentication is required."),
	FORBIDDEN(HttpStatus.FORBIDDEN, Level.WARN, "Access is denied."),
	NOT_FOUND(HttpStatus.NOT_FOUND, Level.INFO, "No endpoint matches this path."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, Level.INFO, "Requested resource was not found."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, Level.INFO, "HTTP method is not supported for this endpoint."),
	NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, Level.INFO, "Requested response media type is not supported."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, Level.INFO, "Content type is not supported."),
	CONFLICT(HttpStatus.CONFLICT, Level.INFO, "Request conflicts with the current state of the resource."),
	AUTHENTICATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Level.WARN, "Authentication service is temporarily unavailable; retry later."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, Level.ERROR, "An unexpected error occurred.");

	private final HttpStatus status;

	private final Level logLevel;

	private final String defaultDetail;

	CommonErrorCode(HttpStatus status, Level logLevel, String defaultDetail) {
		this.status = status;
		this.logLevel = logLevel;
		this.defaultDetail = defaultDetail;
	}

	@Override
	public HttpStatus status() {
		return this.status;
	}

	@Override
	public Level logLevel() {
		return this.logLevel;
	}

	@Override
	public String defaultDetail() {
		return this.defaultDetail;
	}

}
